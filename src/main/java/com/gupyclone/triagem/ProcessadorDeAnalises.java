package com.gupyclone.triagem;

import com.gupyclone.candidaturas.Candidatura;
import com.gupyclone.candidaturas.CandidaturaRepository;
import com.gupyclone.candidaturas.StatusCandidatura;
import com.gupyclone.config.PropriedadesTriagem;
import com.gupyclone.curriculos.Curriculo;
import com.gupyclone.notificacoes.FilaDeEmails;
import com.gupyclone.notificacoes.TipoEmail;
import com.gupyclone.vagas.CriterioAceite;
import com.gupyclone.vagas.Vaga;
import java.time.OffsetDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Tira candidaturas da fila de analise, decide cada uma e avisa o candidato.
 *
 * <p>A classe nao tem {@code @Transactional} de proposito. Processar um job sao
 * tres passos de naturezas diferentes, e e a separacao deles que da as
 * garantias:
 *
 * <ol>
 *   <li>reivindicar, em transacao propria e curta (lease no banco), e montar o
 *       contexto em outra transacao curta, so de leitura;</li>
 *   <li>analisar <em>fora</em> de qualquer transacao — a estrategia pode ser
 *       uma chamada de rede a uma LLM, e segurar uma conexao do pool enquanto
 *       se espera por ela e o jeito mais rapido de esgotar o pool;</li>
 *   <li>gravar o resultado em outra transacao curta, junto com o e-mail.</li>
 * </ol>
 *
 * <p>O passo 3 e o coracao de "nao duplicar". A conclusao da candidatura e um
 * {@code update ... where status = 'RECEBIDA'}, que devolve 1 para quem chegou
 * primeiro e 0 para os demais, e <strong>so quem recebeu 1 enfileira o
 * e-mail</strong>. Com isso reprocessar e inofensivo: o resultado de uma ou de
 * dez passadas e sempre uma transicao de estado e um aviso.
 *
 * <p>E "nao perder nenhuma" vem de duas pecas: o job existe desde a mesma
 * transacao que gravou a candidatura, e um job cujo worker morreu volta para
 * {@code PENDENTE} quando o lease expira ({@link #recuperarLeasesExpirados}).
 */
@Service
public class ProcessadorDeAnalises {

    private static final Logger log = LoggerFactory.getLogger(ProcessadorDeAnalises.class);

    /** O suficiente para diagnosticar sem transformar a coluna num log. */
    private static final int MAXIMO_DO_ERRO = 1000;

    private final AnaliseJobRepository analises;
    private final CandidaturaRepository candidaturas;
    private final ReivindicadorDeAnalises reivindicador;
    private final AnalisadorDeCandidatura analisador;
    private final FilaDeEmails fila;
    private final PropriedadesTriagem propriedades;
    private final TransactionTemplate leitura;
    private final TransactionTemplate escrita;

    public ProcessadorDeAnalises(AnaliseJobRepository analises,
                                 CandidaturaRepository candidaturas,
                                 ReivindicadorDeAnalises reivindicador,
                                 AnalisadorDeCandidatura analisador,
                                 FilaDeEmails fila,
                                 PropriedadesTriagem propriedades,
                                 PlatformTransactionManager gerenciadorDeTransacoes) {
        this.analises = analises;
        this.candidaturas = candidaturas;
        this.reivindicador = reivindicador;
        this.analisador = analisador;
        this.fila = fila;
        this.propriedades = propriedades;
        // REQUIRES_NEW nas duas pela mesma razao do reivindicador: cada passo e
        // curto e independente, mesmo que alguem chame o processador de dentro
        // de uma transacao.
        this.leitura = new TransactionTemplate(gerenciadorDeTransacoes);
        this.leitura.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.leitura.setReadOnly(true);
        this.escrita = new TransactionTemplate(gerenciadorDeTransacoes);
        this.escrita.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Reivindica um lote da fila e analisa cada job dele.
     *
     * @return quantas candidaturas receberam status final <em>nesta</em>
     *         chamada (uma que outro worker concluiu no mesmo instante nao
     *         conta aqui: ela foi contada por quem a concluiu)
     */
    public int processarUmLote() {
        List<Long> reivindicados = reivindicador.reivindicar(propriedades.lote());
        int concluidas = 0;
        for (Long jobId : reivindicados) {
            try {
                if (processar(jobId)) {
                    concluidas++;
                }
            } catch (RuntimeException falhaInesperada) {
                // Falha fora da analise (o banco, por exemplo). O lote continua
                // nos outros jobs e este volta pela expiracao do lease, que e
                // exatamente o caso que o lease existe para cobrir.
                log.error("falha inesperada ao processar o job de analise {}", jobId, falhaInesperada);
            }
        }
        return concluidas;
    }

    /**
     * Devolve a fila os jobs cujo worker morreu depois de reivindicar.
     *
     * @return quantos voltaram para PENDENTE
     */
    public int recuperarLeasesExpirados() {
        return reivindicador.devolverLeasesExpirados(propriedades.lease(), propriedades.maxTentativas());
    }

    /**
     * Devolve a fila os jobs que esgotaram as tentativas ha mais de
     * {@code gupy.triagem.quarentena}.
     *
     * @return quantos voltaram
     */
    public int reenfileirarOsQueFalharam() {
        return reivindicador.tirarDaQuarentena(propriedades.quarentena());
    }

    private boolean processar(Long jobId) {
        ContextoDaAnalise contexto = leitura.execute(status -> montarContexto(jobId));
        if (contexto == null) {
            // Ou o job desapareceu junto com a candidatura (cascade), ou a
            // candidatura ja tem status final — uma passada anterior morreu
            // entre concluir a candidatura e concluir o job. Nos dois casos nao
            // ha analise a fazer, e fechar o job encerra o assunto sem segundo
            // e-mail.
            escrita.executeWithoutResult(status -> analises.findById(jobId).ifPresent(AnaliseJob::concluir));
            return false;
        }

        ResultadoDaAnalise resultado;
        try {
            resultado = analisador.analisar(contexto);
        } catch (RuntimeException falha) {
            log.warn("analise da candidatura {} falhou: {}", contexto.candidaturaId(), descrever(falha));
            escrita.executeWithoutResult(status -> analises.findById(jobId).ifPresent(job ->
                    job.registrarFalha(descrever(falha),
                            propriedades.maxTentativas(),
                            propriedades.esperaAposFalha())));
            return false;
        }

        Integer linhas = escrita.execute(status -> gravar(jobId, contexto, resultado));
        return linhas != null && linhas == 1;
    }

    /**
     * Tudo o que a analise precisa, lido numa transacao curta.
     *
     * <p>O contexto e um record de dados primitivos, e e isso que permite
     * analisar fora de transacao: nada que o analisador toque e uma associacao
     * preguicosa esperando uma sessao aberta.
     *
     * @return {@code null} quando nao ha analise a fazer
     */
    private ContextoDaAnalise montarContexto(Long jobId) {
        AnaliseJob job = analises.findById(jobId).orElse(null);
        if (job == null) {
            return null;
        }
        Candidatura candidatura = job.getCandidatura();
        if (candidatura.getStatus().ehFinal()) {
            return null;
        }
        Vaga vaga = candidatura.getVaga();
        Curriculo curriculo = candidatura.getCurriculo();
        List<ContextoDaAnalise.Criterio> criterios = vaga.getCriterios().stream()
                .map(ProcessadorDeAnalises::comoDado)
                .toList();
        return new ContextoDaAnalise(candidatura.getId(),
                vaga.getId(),
                vaga.getTitulo(),
                vaga.getDescricao(),
                vaga.getPontuacaoMinima(),
                curriculo.getId(),
                curriculo.getTexto(),
                criterios);
    }

    /**
     * A transacao que decide a candidatura e o aviso: os dois juntos ou nenhum.
     *
     * @return 1 se foi esta chamada que concluiu a candidatura, 0 se outra
     *         chegou primeiro
     */
    private int gravar(Long jobId, ContextoDaAnalise contexto, ResultadoDaAnalise resultado) {
        StatusCandidatura novoStatus = resultado.aprovado()
                ? StatusCandidatura.APROVADA
                : StatusCandidatura.REPROVADA;
        int linhas = candidaturas.concluir(contexto.candidaturaId(),
                novoStatus,
                resultado.pontuacao(),
                resultado.motivo(),
                OffsetDateTime.now());

        if (linhas == 1) {
            // Carregada DEPOIS do concluir, nunca antes: aquele update em massa
            // limpa o contexto de persistencia, e uma Candidatura lida antes
            // chegaria destacada ao redator do e-mail, que estouraria
            // LazyInitializationException ao ler o candidato e a vaga.
            Candidatura gerenciada = candidaturas.findById(contexto.candidaturaId())
                    .orElseThrow(() -> new IllegalStateException(
                            "candidatura " + contexto.candidaturaId() + " concluida e desaparecida na mesma transacao"));
            fila.enfileirar(gerenciada, tipoDoAviso(novoStatus), resultado.pontuacao(), resultado.motivo());
        } else {
            // Outro worker concluiu esta candidatura entre a nossa reivindicacao
            // e agora. Quem recebeu 1 avisou o candidato; aqui nao se avisa de
            // novo, e e so isso que impede o segundo e-mail.
            log.debug("candidatura {} ja havia sido concluida por outra passada", contexto.candidaturaId());
        }

        analises.findById(jobId).ifPresent(AnaliseJob::concluir);
        return linhas;
    }

    private static ContextoDaAnalise.Criterio comoDado(CriterioAceite criterio) {
        return new ContextoDaAnalise.Criterio(criterio.getId(),
                criterio.getDescricao(),
                criterio.getExpressao(),
                criterio.getPeso(),
                criterio.isObrigatorio());
    }

    private static TipoEmail tipoDoAviso(StatusCandidatura status) {
        return status == StatusCandidatura.APROVADA
                ? TipoEmail.CANDIDATURA_APROVADA
                : TipoEmail.CANDIDATURA_REPROVADA;
    }

    private static String descrever(Exception falha) {
        String descricao = falha.getClass().getSimpleName()
                + (falha.getMessage() == null ? "" : ": " + falha.getMessage());
        return descricao.length() <= MAXIMO_DO_ERRO ? descricao : descricao.substring(0, MAXIMO_DO_ERRO);
    }
}
