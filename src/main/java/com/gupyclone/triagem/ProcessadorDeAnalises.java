package com.gupyclone.triagem;

import com.gupyclone.candidaturas.Candidaturas;
import com.gupyclone.candidaturas.DadosParaAnalise;
import com.gupyclone.candidaturas.StatusCandidatura;
import com.gupyclone.config.PropriedadesTriagem;
import com.gupyclone.notificacoes.AvisoAoCandidato;
import com.gupyclone.notificacoes.FilaDeEmails;
import com.gupyclone.notificacoes.TipoEmail;
import com.gupyclone.pessoas.ConsultaDePessoas;
import com.gupyclone.pessoas.DadosDoCandidato;
import com.gupyclone.vagas.ConsultaDeVagas;
import com.gupyclone.vagas.DadosDaVaga;
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
 * <p>Candidatura, vaga e candidato sao outros agregados: chegam aqui pelas
 * portas dos modulos donos, em forma de dados. A unica entidade que esta classe
 * conhece e o {@link AnaliseJob}, que e dela.
 *
 * <p>A classe nao tem {@code @Transactional} de proposito. Processar um job sao
 * tres passos de naturezas diferentes, e e a separacao deles que da as
 * garantias:
 *
 * <ol>
 *   <li>reivindicar, em transacao propria e curta (lease no banco), e reunir o
 *       que a decisao precisa em outra transacao curta, so de leitura;</li>
 *   <li>analisar <em>fora</em> de qualquer transacao — a estrategia pode ser
 *       uma chamada de rede a uma LLM, e segurar uma conexao do pool enquanto
 *       se espera por ela e o jeito mais rapido de esgotar o pool;</li>
 *   <li>gravar o resultado em outra transacao curta, junto com o e-mail.</li>
 * </ol>
 *
 * <p>O passo 3 e o coracao de "nao duplicar". A conclusao da candidatura e um
 * {@code update ... where status = 'RECEBIDA'}, e a porta devolve verdadeiro
 * para quem chegou primeiro e falso para os demais: <strong>so quem recebe
 * verdadeiro enfileira o e-mail</strong>. Com isso reprocessar e inofensivo: o
 * resultado de uma ou de dez passadas e sempre uma transicao de estado e um
 * aviso.
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
    private final Candidaturas candidaturas;
    private final ConsultaDeVagas vagas;
    private final ConsultaDePessoas pessoas;
    private final InsumosDaAnalise insumos;
    private final ReivindicadorDeAnalises reivindicador;
    private final AnalisadorDeCandidatura analisador;
    private final FilaDeEmails fila;
    private final PropriedadesTriagem propriedades;
    private final TransactionTemplate leitura;
    private final TransactionTemplate escrita;

    public ProcessadorDeAnalises(AnaliseJobRepository analises,
                                 Candidaturas candidaturas,
                                 ConsultaDeVagas vagas,
                                 ConsultaDePessoas pessoas,
                                 InsumosDaAnalise insumos,
                                 ReivindicadorDeAnalises reivindicador,
                                 AnalisadorDeCandidatura analisador,
                                 FilaDeEmails fila,
                                 PropriedadesTriagem propriedades,
                                 PlatformTransactionManager gerenciadorDeTransacoes) {
        this.analises = analises;
        this.candidaturas = candidaturas;
        this.vagas = vagas;
        this.pessoas = pessoas;
        this.insumos = insumos;
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
        AnaliseAFazer aFazer = leitura.execute(status -> reunir(jobId));
        if (aFazer == null) {
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
            resultado = analisador.analisar(aFazer.contexto());
        } catch (RuntimeException falha) {
            log.warn("analise da candidatura {} falhou: {}",
                    aFazer.contexto().candidaturaId(), descrever(falha));
            escrita.executeWithoutResult(status -> analises.findById(jobId).ifPresent(job ->
                    job.registrarFalha(descrever(falha),
                            propriedades.maxTentativas(),
                            propriedades.esperaAposFalha())));
            return false;
        }

        return Boolean.TRUE.equals(escrita.execute(status -> gravar(jobId, aFazer, resultado)));
    }

    /**
     * Tudo o que decidir uma candidatura precisa, lido numa transacao curta: o
     * contexto que o analisador recebe e para quem vai o aviso.
     *
     * @return {@code null} quando nao ha analise a fazer
     */
    private AnaliseAFazer reunir(Long jobId) {
        AnaliseJob job = analises.findById(jobId).orElse(null);
        if (job == null) {
            return null;
        }
        DadosParaAnalise candidatura = candidaturas.paraAnalise(job.getCandidaturaId()).orElse(null);
        if (candidatura == null || candidatura.status().ehFinal()) {
            return null;
        }
        DadosDaVaga vaga = vagas.porId(candidatura.vagaId()).orElseThrow(() -> ausente(
                "vaga " + candidatura.vagaId(), candidatura.candidaturaId()));
        DadosDoCandidato candidato = pessoas.candidato(candidatura.candidatoId()).orElseThrow(() -> ausente(
                "candidato " + candidatura.candidatoId(), candidatura.candidaturaId()));
        ContextoDaAnalise contexto = new ContextoDaAnalise(candidatura.candidaturaId(),
                vaga.id(),
                vaga.titulo(),
                vaga.descricao(),
                vaga.pontuacaoMinima(),
                candidatura.curriculoId(),
                insumos.textoDoCurriculo(candidatura.curriculoId()),
                insumos.criteriosDaVaga(vaga.id()));
        return new AnaliseAFazer(contexto, candidato);
    }

    /**
     * A transacao que decide a candidatura e o aviso: os dois juntos ou nenhum.
     *
     * @return true se foi esta chamada que concluiu a candidatura, false se
     *         outra chegou primeiro
     */
    private boolean gravar(Long jobId, AnaliseAFazer aFazer, ResultadoDaAnalise resultado) {
        ContextoDaAnalise contexto = aFazer.contexto();
        StatusCandidatura novoStatus = resultado.aprovado()
                ? StatusCandidatura.APROVADA
                : StatusCandidatura.REPROVADA;
        boolean concluida = candidaturas.concluir(contexto.candidaturaId(),
                novoStatus,
                resultado.pontuacao(),
                resultado.motivo());

        if (concluida) {
            // O aviso leva os dados lidos no passo 1. Nao ha entidade em mao
            // para o update em massa da conclusao destacar, que era a armadilha
            // de quando a candidatura carregava candidato e vaga como
            // associacoes.
            fila.enfileirar(new AvisoAoCandidato(contexto.candidaturaId(),
                    tipoDoAviso(novoStatus),
                    aFazer.candidato().email(),
                    aFazer.candidato().nome(),
                    contexto.tituloDaVaga(),
                    resultado.pontuacao(),
                    resultado.motivo()));
        } else {
            // Outro worker concluiu esta candidatura entre a nossa reivindicacao
            // e agora. Quem concluiu avisou o candidato; aqui nao se avisa de
            // novo, e e so isso que impede o segundo e-mail.
            log.debug("candidatura {} ja havia sido concluida por outra passada", contexto.candidaturaId());
        }

        analises.findById(jobId).ifPresent(AnaliseJob::concluir);
        return concluida;
    }

    /** O contexto da analise e o candidato que recebe o aviso, lidos juntos. */
    private record AnaliseAFazer(ContextoDaAnalise contexto, DadosDoCandidato candidato) {
    }

    private static IllegalStateException ausente(String oQue, long candidaturaId) {
        return new IllegalStateException(oQue + " da candidatura " + candidaturaId + " nao existe mais");
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
