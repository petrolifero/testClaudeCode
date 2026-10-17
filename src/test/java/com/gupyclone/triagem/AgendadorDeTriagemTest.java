package com.gupyclone.triagem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.gupyclone.candidaturas.Candidatura;
import com.gupyclone.candidaturas.StatusCandidatura;
import com.gupyclone.vagas.Vaga;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;

/**
 * O unico teste da fatia que liga o agendador. Os outros rodam com
 * {@code gupy.agendamento.ativo=false} para decidirem quando a fila e
 * processada; aqui a propriedade e ligada justamente para provar que, em
 * producao, ninguem precisa chamar o processador na mao: a candidatura e
 * submetida e nada mais e feito.
 *
 * <p>O intervalo da fila de e-mails fica longo de proposito: o que se prova
 * aqui e a triagem drenando, nao o SMTP, e nao ha GreenMail nesta classe.
 */
@TestPropertySource(properties = {
        "gupy.agendamento.ativo=true",
        "gupy.triagem.intervalo=PT0.2S",
        "gupy.email.intervalo=PT1H"
})
class AgendadorDeTriagemTest extends TesteDeTriagem {

    @Autowired
    private ApplicationContext contexto;

    @Test
    @DisplayName("a varredura automatica drena a fila de analise sozinha")
    void varreduraAutomaticaDrenaAFila() {
        assertThat(contexto.getBeanNamesForType(AgendadorDeTriagem.class)).isNotEmpty();

        Vaga vaga = publicarVagaDeExemplo(60);
        Candidatura deCarlos = candidatar(carlos, vaga.getId(), PDF_QUE_ATENDE);
        Candidatura deJoao = candidatar(joao, vaga.getId(), PDF_QUE_NAO_ATENDE);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertThat(candidaturas.findById(deCarlos.getId()).orElseThrow().getStatus())
                    .isEqualTo(StatusCandidatura.APROVADA);
            assertThat(candidaturas.findById(deJoao.getId()).orElseThrow().getStatus())
                    .isEqualTo(StatusCandidatura.REPROVADA);
            assertThat(analises.countByStatus(StatusJob.CONCLUIDO)).isEqualTo(2);
            assertThat(analises.countByStatus(StatusJob.PENDENTE)).isZero();
            assertThat(analises.countByStatus(StatusJob.PROCESSANDO)).isZero();
            assertThat(emails.countByCandidaturaId(deCarlos.getId())).isEqualTo(1);
            assertThat(emails.countByCandidaturaId(deJoao.getId())).isEqualTo(1);
        });

        // A varredura continua rodando e nao deve mexer no que ja foi decidido.
        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(emails.count()).isEqualTo(2));
    }
}
