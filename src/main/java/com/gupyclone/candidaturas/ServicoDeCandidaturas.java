package com.gupyclone.candidaturas;

import com.gupyclone.curriculos.Curriculo;
import com.gupyclone.curriculos.CurriculoRepository;
import com.gupyclone.curriculos.RecebedorDeCurriculos;
import com.gupyclone.pessoas.Candidato;
import com.gupyclone.pessoas.CandidatoRepository;
import com.gupyclone.triagem.AnaliseJob;
import com.gupyclone.triagem.AnaliseJobRepository;
import com.gupyclone.vagas.Vaga;
import com.gupyclone.vagas.VagaInexistenteException;
import com.gupyclone.vagas.VagaRepository;
import java.util.List;
import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Candidatar-se a uma vaga enviando curriculo.
 *
 * <p>A classe <strong>nao</strong> abre uma transacao em volta de
 * {@link #candidatar}, e isso e o centro da fatia. Uma candidatura e uma
 * sequencia de etapas com naturezas diferentes, e a ordem delas e o que da as
 * garantias:
 *
 * <ol>
 *   <li>recusar o que ja se sabe recusar — vaga fechada e candidatura
 *       repetida — <em>antes</em> de gastar extracao de texto;</li>
 *   <li>receber o curriculo <em>fora</em> de transacao, porque o OCR de um PDF
 *       digitalizado leva segundos por pagina (ver
 *       {@link RecebedorDeCurriculos#receber});</li>
 *   <li>gravar candidatura e job de analise em <em>uma</em> transacao curta, os
 *       dois juntos ou nenhum;</li>
 *   <li>traduzir a colisao do indice unico, que e quem decide a corrida.</li>
 * </ol>
 */
@Service
public class ServicoDeCandidaturas {

    private static final Logger log = LoggerFactory.getLogger(ServicoDeCandidaturas.class);

    private final VagaRepository vagas;
    private final CandidatoRepository candidatos;
    private final CurriculoRepository curriculos;
    private final CandidaturaRepository candidaturas;
    private final AnaliseJobRepository analises;
    private final RecebedorDeCurriculos recebedor;
    private final TransactionTemplate gravacao;

    public ServicoDeCandidaturas(VagaRepository vagas,
                                 CandidatoRepository candidatos,
                                 CurriculoRepository curriculos,
                                 CandidaturaRepository candidaturas,
                                 AnaliseJobRepository analises,
                                 RecebedorDeCurriculos recebedor,
                                 PlatformTransactionManager gerenciadorDeTransacoes) {
        this.vagas = vagas;
        this.candidatos = candidatos;
        this.curriculos = curriculos;
        this.candidaturas = candidaturas;
        this.analises = analises;
        this.recebedor = recebedor;
        this.gravacao = new TransactionTemplate(gerenciadorDeTransacoes);
    }

    /**
     * Inscreve o candidato na vaga com o curriculo enviado.
     *
     * @return a candidatura gravada, com vaga, candidato e curriculo carregados
     * @throws VagaInexistenteException     se a vaga nao existir
     * @throws VagaFechadaException         se a vaga nao estiver aberta
     * @throws CandidaturaDuplicadaException se o candidato ja estiver inscrito,
     *                                      inclusive quando a inscricao rival
     *                                      acontece no mesmo instante
     * @throws com.gupyclone.curriculos.CurriculoInvalidoException se o PDF for ilegivel
     * @throws org.springframework.transaction.IllegalTransactionStateException
     *         se for chamado de dentro de uma transacao
     */
    // NEVER, e nao a ausencia de anotacao: a etapa 2 e longa (OCR) e nao pode
    // ser arrastada para a transacao de quem chama, nem o catch de colisao pode
    // receber uma transacao alheia ja abortada para traduzir. E o espelho do
    // MANDATORY da fila de e-mails: em vez de confiar no comentario, a
    // propagacao recusa o chamador errado na primeira chamada.
    @Transactional(propagation = Propagation.NEVER)
    public Candidatura candidatar(Long candidatoId, Long vagaId, String nomeArquivo, byte[] pdf) {
        if (candidatoId == null) {
            throw new IllegalArgumentException("candidatura precisa de um candidato");
        }
        Vaga vaga = vagas.findById(vagaId).orElseThrow(() -> new VagaInexistenteException(vagaId));
        if (!vaga.estaAberta()) {
            throw new VagaFechadaException(vaga.getTitulo());
        }
        // Falha rapida: a checagem nao substitui o unique do banco (duas
        // chamadas simultaneas passam as duas por aqui), mas evita gastar
        // segundos de OCR no caso comum, que e a pessoa clicando duas vezes.
        if (candidaturas.existsByVagaIdAndCandidatoId(vagaId, candidatoId)) {
            throw new CandidaturaDuplicadaException(vaga.getTitulo());
        }

        Curriculo curriculo = recebedor.receber(candidatoId, nomeArquivo, pdf);

        try {
            return gravacao.execute(status -> gravar(vagaId, candidatoId, curriculo.getId()));
        } catch (DataIntegrityViolationException colisao) {
            // O catch fica FORA da transacao de proposito. Ao violar um unique
            // o PostgreSQL aborta a transacao inteira: engolir a excecao dentro
            // dela deixaria a transacao envenenada, e qualquer comando seguinte
            // (inclusive o commit) falharia com "current transaction is
            // aborted". Aqui a transacao ja sofreu rollback e so resta dizer ao
            // candidato, em 409, o que o indice unico decidiu.
            //
            // O curriculo ja gravado fica no banco. Ele foi recebido em
            // transacao propria, e nenhuma constraint o obriga a ter
            // candidatura: e o arquivo que a pessoa enviou, varios curriculos
            // por candidato sao esperados, e apagar exigiria uma escrita
            // compensatoria depois de uma transacao que acabou de falhar — mais
            // um caminho de erro para economizar um PDF que ainda serve de
            // registro do que foi enviado.
            log.debug("candidatura do candidato {} na vaga {} perdeu a corrida no indice unico",
                    candidatoId, vagaId, colisao);
            throw new CandidaturaDuplicadaException(vaga.getTitulo());
        }
    }

    @Transactional(readOnly = true)
    public List<CandidaturaResposta> doCandidato(Long candidatoId) {
        return candidaturas.resumosDoCandidato(candidatoId);
    }

    /**
     * Candidaturas de uma vaga, para o RH que a publicou.
     *
     * @throws AccessDeniedException se o recrutador nao for o dono da vaga
     */
    @Transactional(readOnly = true)
    public List<CandidaturaResposta> daVaga(Long vagaId, Long recrutadorId) {
        Vaga vaga = vagas.findById(vagaId).orElseThrow(() -> new VagaInexistenteException(vagaId));
        if (recrutadorId == null || !vaga.getRecrutador().getId().equals(recrutadorId)) {
            throw new AccessDeniedException("somente quem publicou a vaga vê as candidaturas dela");
        }
        return candidaturas.resumosDaVaga(vagaId);
    }

    /**
     * A transacao curta da etapa 3: candidatura e job de analise juntos.
     *
     * <p>E dessa atomicidade que vem "se a candidatura existe, o job existe":
     * nao ha janela em que a inscricao esteja gravada e a triagem nao saiba
     * dela.
     */
    private Candidatura gravar(Long vagaId, Long candidatoId, Long curriculoId) {
        Vaga vaga = vagas.findById(vagaId).orElseThrow(() -> new VagaInexistenteException(vagaId));
        if (!vaga.estaAberta()) {
            // Releitura, nao repeticao: a vaga pode ter sido fechada enquanto o
            // curriculo era lido, e a decisao que vale e a desta transacao.
            throw new VagaFechadaException(vaga.getTitulo());
        }
        Candidato candidato = candidatos.findById(candidatoId)
                .orElseThrow(() -> new IllegalArgumentException("candidato inexistente: " + candidatoId));
        Curriculo curriculo = curriculos.findById(curriculoId)
                .orElseThrow(() -> new IllegalStateException("curriculo recebido desapareceu: " + curriculoId));

        // saveAndFlush, e nao save: com save o insert seria adiado para o
        // commit, e a violacao do unique chegaria a quem chamou como falha de
        // commit em vez de DataIntegrityViolationException traduzivel.
        Candidatura candidatura = candidaturas.saveAndFlush(new Candidatura(vaga, candidato, curriculo));
        analises.saveAndFlush(new AnaliseJob(candidatura));
        return carregada(candidatura);
    }

    /**
     * Carrega o que a resposta precisa antes de a transacao fechar.
     *
     * <p>Com {@code open-in-view: false} a candidatura chega destacada a quem
     * chamou, e vaga, candidato e curriculo sao associacoes preguicosas: sem
     * isso, montar a resposta estouraria {@code LazyInitializationException}.
     */
    private static Candidatura carregada(Candidatura candidatura) {
        Hibernate.initialize(candidatura.getVaga());
        Hibernate.initialize(candidatura.getCandidato());
        Hibernate.initialize(candidatura.getCurriculo());
        return candidatura;
    }

}
