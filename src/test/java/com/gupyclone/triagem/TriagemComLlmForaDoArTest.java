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
 * aprovado por engano e nada perdido. O job volta para a fila com a tentativa
 * contada e o erro gravado; esgotadas as tentativas ele vira FALHO; e passada a
 * quarentena ele VOLTA para a fila. A candidatura continua RECEBIDA enquanto a
 * analise nao decide, e nenhum e-mail e enfileirado nesse meio-tempo.
 *
 * <p>A quarentena existe por causa desta revisao: antes, FALHO era terminal e
 * nenhum codigo o devolvia a fila, de modo que uma indisponibilidade de
 * segundos deixava a candidatura sem decisao para sempre. Este teste chamava
 * esse desfecho de correto; agora ele prova a recuperacao.
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
        // O bean publicado e o decorador de veto; o nome delegado e o que diz
        // qual estrategia esta de pe.
        assertThat(escolhido.nome()).isEqualTo(AnalisadorViaLlm.NOME);
        assertThat(escolhido).isInstanceOf(AnalisadorComVetoDeObrigatorios.class);
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

        // Enquanto a quarentena nao vence, o job FALHO descansa: nao e
        // reivindicado nem devolvido pelo lease. O custo disso esta explicito
        // aqui: a candidatura segue sem decisao e ninguem foi avisado.
        assertThat(processador.processarUmLote()).as("FALHO nao e reivindicado de imediato").isZero();
        assertThat(processador.recuperarLeasesExpirados()).as("FALHO nao e assunto do lease").isZero();
        assertThat(candidaturas.findAllByStatus(StatusCandidatura.RECEBIDA)).hasSize(1);
        assertThat(emails.count()).isZero();
    }

    @Test
    @DisplayName("passada a quarentena o job FALHO volta para a fila: FALHO nao e terminal")
    void quarentenaDevolveOJobQueFalhou() {
        Vaga vaga = publicarVagaDeExemplo(60);
        Candidatura candidatura = candidatar(carlos, vaga.getId(), PDF_QUE_ATENDE);
        Long jobId = analises.findByCandidaturaId(candidatura.getId()).orElseThrow().getId();

        // Esgota as tentativas contra a LLM fora do ar.
        for (int tentativa = 0; tentativa < 3; tentativa++) {
            processador.processarUmLote();
        }
        assertThat(analises.findById(jobId).orElseThrow().getStatus()).isEqualTo(StatusJob.FALHO);

        // Envelhece o job para a quarentena vencer (PT1H no perfil de teste).
        jdbc.sql("update analise_job set atualizado_em = now() - interval '2 hours' where id = ?")
                .param(jobId).update();

        assertThat(processador.reenfileirarOsQueFalharam())
                .as("o job volta para a fila em vez de ficar esquecido")
                .isEqualTo(1);

        AnaliseJob devolvido = analises.findById(jobId).orElseThrow();
        assertThat(devolvido.getStatus()).isEqualTo(StatusJob.PENDENTE);
        assertThat(devolvido.getTentativas())
                .as("o orcamento de tentativas recomeca: a falha anterior pode ter sido transitoria")
                .isZero();

        // E o processamento volta a acontecer de verdade (aqui falha de novo,
        // porque a LLM continua fora do ar -- mas foi TENTADO).
        assertThat(processador.processarUmLote()).isZero();
        assertThat(analises.findById(jobId).orElseThrow().getTentativas()).isEqualTo(1);
    }
}
