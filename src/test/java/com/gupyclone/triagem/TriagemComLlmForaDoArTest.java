package com.gupyclone.triagem;

import static org.assertj.core.api.Assertions.assertThat;

import com.gupyclone.candidaturas.Candidatura;
import com.gupyclone.candidaturas.StatusCandidatura;
import com.gupyclone.vagas.Vaga;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/**
 * O caminho de falha de verdade, sem nenhum duble: a estrategia e a por LLM e
 * {@code gupy.llm.base-url} aponta para uma porta onde ninguem escuta.
 *
 * <p>O que se prova e a prioridade do projeto quando a analise quebra: nada
 * aprovado por engano, nada perdido em silencio. O job volta para a fila com a
 * tentativa contada e o erro gravado; esgotadas as tentativas ele vira FALHO e
 * fica na tabela para auditoria; a candidatura continua RECEBIDA o tempo todo e
 * nenhum e-mail e enfileirado.
 */
@TestPropertySource(properties = {
        "gupy.triagem.estrategia=llm",
        // Porta 1: a conexao e recusada na hora, sem depender de timeout.
        "gupy.llm.base-url=http://127.0.0.1:1",
        "gupy.llm.timeout=PT2S"
})
class TriagemComLlmForaDoArTest extends TesteDeTriagem {

    @Autowired
    private ProcessadorDeAnalises processador;

    @Autowired
    private AnalisadorDeCandidatura escolhido;

    @Test
    @DisplayName("com gupy.triagem.estrategia=llm quem analisa e o analisador por LLM")
    void estrategiaLlmFoiEscolhida() {
        assertThat(escolhido).isInstanceOf(AnalisadorViaLlm.class);
        assertThat(escolhido.nome()).isEqualTo(AnalisadorViaLlm.NOME);
    }

    @Test
    @DisplayName("analise que falha devolve o job, mantem a candidatura RECEBIDA e nao manda e-mail")
    void falhaDeAnaliseDevolveOJobSemAprovarNada() {
        Vaga vaga = publicarVagaDeExemplo(60);
        Candidatura candidatura = candidatar(carlos, vaga.getId(), PDF_QUE_ATENDE);
        Long jobId = analises.findByCandidaturaId(candidatura.getId()).orElseThrow().getId();

        assertThat(processador.processarUmLote()).as("nada foi concluido").isZero();

        AnaliseJob depoisDaFalha = analises.findById(jobId).orElseThrow();
        assertThat(depoisDaFalha.getStatus()).isEqualTo(StatusJob.PENDENTE);
        assertThat(depoisDaFalha.getTentativas()).isEqualTo(1);
        assertThat(depoisDaFalha.getUltimoErro()).contains("AnaliseIndisponivel").contains("127.0.0.1:1");
        assertThat(depoisDaFalha.getReivindicadoEm()).isNull();

        Candidatura intocada = candidaturas.findById(candidatura.getId()).orElseThrow();
        assertThat(intocada.getStatus()).isEqualTo(StatusCandidatura.RECEBIDA);
        assertThat(intocada.getPontuacao()).isNull();
        assertThat(intocada.getMotivo()).isNull();
        assertThat(intocada.getAnalisadaEm()).isNull();
        assertThat(emails.count()).isZero();

        // Esgotadas as tentativas (3 em application-test.yml) o job vira FALHO
        // e para de ser reprocessado, mas permanece na tabela: a candidatura
        // nao desaparece, ela fica visivel como pendencia.
        assertThat(processador.processarUmLote()).isZero();
        assertThat(processador.processarUmLote()).isZero();

        AnaliseJob esgotado = analises.findById(jobId).orElseThrow();
        assertThat(esgotado.getStatus()).isEqualTo(StatusJob.FALHO);
        assertThat(esgotado.getTentativas()).isEqualTo(3);
        assertThat(esgotado.getUltimoErro()).isNotBlank();

        assertThat(processador.processarUmLote()).as("FALHO nao e reivindicado").isZero();
        assertThat(processador.recuperarLeasesExpirados()).as("FALHO nao e devolvido pelo lease").isZero();
        assertThat(candidaturas.findAllByStatus(StatusCandidatura.RECEBIDA)).hasSize(1);
        assertThat(emails.count()).isZero();
    }
}
