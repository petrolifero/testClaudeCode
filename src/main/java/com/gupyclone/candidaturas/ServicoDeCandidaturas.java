package com.gupyclone.candidaturas;

import com.gupyclone.curriculos.ConsultaDeCurriculos;
import com.gupyclone.curriculos.RecebedorDeCurriculos;
import com.gupyclone.pessoas.ConsultaDePessoas;
import com.gupyclone.vagas.ConsultaDeVagas;
import com.gupyclone.vagas.DadosDaVaga;
import com.gupyclone.vagas.VagaInexistenteException;
import java.util.List;
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
 * <p>Um unico repositorio, o do proprio agregado. Vaga, candidato e curriculo
 * sao outros agregados e chegam pelas portas dos modulos donos, em forma de
 * dados: nada aqui tem associacao preguicosa para inicializar.
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

    private final CandidaturaRepository candidaturas;
    private final ConsultaDeVagas vagas;
    private final ConsultaDePessoas pessoas;
    private final ConsultaDeCurriculos curriculos;
    private final RecebedorDeCurriculos recebedor;
    private final EnfileiramentoDeTriagem triagem;
    private final TransactionTemplate gravacao;

    public ServicoDeCandidaturas(CandidaturaRepository candidaturas,
                                 ConsultaDeVagas vagas,
                                 ConsultaDePessoas pessoas,
                                 ConsultaDeCurriculos curriculos,
                                 RecebedorDeCurriculos recebedor,
                                 EnfileiramentoDeTriagem triagem,
                                 PlatformTransactionManager gerenciadorDeTransacoes) {
        this.candidaturas = candidaturas;
        this.vagas = vagas;
        this.pessoas = pessoas;
        this.curriculos = curriculos;
        this.recebedor = recebedor;
        this.triagem = triagem;
        this.gravacao = new TransactionTemplate(gerenciadorDeTransacoes);
    }

    /**
     * Inscreve o candidato na vaga com o curriculo enviado.
     *
     * @return a candidatura gravada, por projecao, pronta para a resposta
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
    public CandidaturaResposta candidatar(Long candidatoId, Long vagaId, String nomeArquivo, byte[] pdf) {
        if (candidatoId == null) {
            throw new IllegalArgumentException("candidatura precisa de um candidato");
        }
        DadosDaVaga vaga = vaga(vagaId);
        if (!vaga.estaAberta()) {
            throw new VagaFechadaException(vaga.titulo());
        }
        // Falha rapida: a checagem nao substitui o unique do banco (duas
        // chamadas simultaneas passam as duas por aqui), mas evita gastar
        // segundos de OCR no caso comum, que e a pessoa clicando duas vezes.
        if (candidaturas.existsByVagaIdAndCandidatoId(vagaId, candidatoId)) {
            throw new CandidaturaDuplicadaException(vaga.titulo());
        }

        Long curriculoId = recebedor.receber(candidatoId, nomeArquivo, pdf).getId();

        try {
            return gravacao.execute(status -> gravar(vagaId, candidatoId, curriculoId));
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
            throw new CandidaturaDuplicadaException(vaga.titulo());
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
        DadosDaVaga vaga = vaga(vagaId);
        if (recrutadorId == null || !vaga.recrutadorId().equals(recrutadorId)) {
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
     *
     * @return a candidatura como a resposta a mostra
     */
    private CandidaturaResposta gravar(Long vagaId, Long candidatoId, Long curriculoId) {
        DadosDaVaga vaga = vaga(vagaId);
        if (!vaga.estaAberta()) {
            // Releitura, nao repeticao: a vaga pode ter sido fechada enquanto o
            // curriculo era lido, e a decisao que vale e a desta transacao.
            throw new VagaFechadaException(vaga.titulo());
        }
        // Confere os dois ids antes de gravar para que id inexistente apareca
        // como erro nomeado, e nao como violacao de chave estrangeira — que o
        // catch de quem chama traduziria como candidatura duplicada.
        pessoas.candidato(candidatoId)
                .orElseThrow(() -> new IllegalArgumentException("candidato inexistente: " + candidatoId));
        curriculos.porId(curriculoId)
                .orElseThrow(() -> new IllegalStateException("curriculo recebido desapareceu: " + curriculoId));

        // saveAndFlush, e nao save: com save o insert seria adiado para o
        // commit, e a violacao do unique chegaria a quem chamou como falha de
        // commit em vez de DataIntegrityViolationException traduzivel.
        Candidatura candidatura = candidaturas.saveAndFlush(
                new Candidatura(vagaId, candidatoId, curriculoId, vaga.recrutadorId()));
        triagem.enfileirar(candidatura.getId());

        // A resposta e lida aqui dentro, e nao depois do commit: fora da
        // transacao a triagem pode ter concluido a candidatura no intervalo, e
        // quem acabou de se inscrever receberia um status que ela nao tinha
        // quando foi gravada.
        return candidaturas.resumoPorId(candidatura.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "candidatura " + candidatura.getId() + " gravada e nao encontrada na mesma transacao"));
    }

    /** A porta devolve vazio para vaga inexistente; o 404 de dominio e daqui. */
    private DadosDaVaga vaga(Long vagaId) {
        return vagas.porId(vagaId).orElseThrow(() -> new VagaInexistenteException(vagaId));
    }
}
