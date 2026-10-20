package com.gupyclone.notificacoes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/**
 * O unico teste que liga o agendador. Os outros rodam com
 * {@code gupy.agendamento.ativo=false} para decidirem quando a fila e
 * processada; aqui a propriedade e ligada justamente para provar que, em
 * producao, ninguem precisa chamar o despachante na mao.
 */
@TestPropertySource(properties = {
        "gupy.agendamento.ativo=true",
        "gupy.email.intervalo=PT0.2S"
})
class AgendadorDeEmailsTest extends TesteDeNotificacoes {

    @Test
    @DisplayName("a varredura automatica drena a fila sozinha")
    void varreduraAutomaticaDrenaAFila() {
        Long carlos = gravarCandidatura("carlos", "Pessoa Desenvolvedora Java Sênior");
        Long joao = gravarCandidatura("joao", "Pessoa Desenvolvedora Java Sênior");
        enfileirar(carlos, TipoEmail.CANDIDATURA_APROVADA, 91, null);
        enfileirar(joao, TipoEmail.CANDIDATURA_REPROVADA, 15, "Critérios obrigatórios não atendidos.");

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(smtp.getReceivedMessages()).hasSize(2);
            assertThat(emails.countByStatus(StatusEmail.ENVIADO)).isEqualTo(2);
            assertThat(emails.countByStatus(StatusEmail.PENDENTE)).isZero();
            assertThat(emails.countByStatus(StatusEmail.ENVIANDO)).isZero();
        });
    }
}
