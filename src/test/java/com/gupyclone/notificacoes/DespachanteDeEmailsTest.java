package com.gupyclone.notificacoes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import jakarta.mail.internet.MimeMessage;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Prova do despacho: entrega por SMTP de verdade, exatamente uma vez por aviso,
 * com varios workers ao mesmo tempo e sobrevivendo a um servidor fora do ar.
 */
class DespachanteDeEmailsTest extends TesteDeNotificacoes {

    private static final String VAGA = "Pessoa Desenvolvedora Java Sênior";
    private static final String MOTIVO =
            "Critérios não atendidos: inglês fluente e pós-graduação em computação.";

    @Autowired
    private DespachanteDeEmails despachante;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private ApplicationContext contexto;

    @Test
    @DisplayName("sem gupy.agendamento.ativo o agendador nem chega a existir")
    void agendadorDesligadoNaSuite() {
        // E o que torna confiaveis as contagens exatas desta classe: ninguem
        // esta drenando a fila por tras das asserticoes.
        assertThat(contexto.getBeanNamesForType(AgendadorDeEmails.class)).isEmpty();
    }

    @Test
    @DisplayName("o aviso de reprovacao chega por SMTP com vaga, motivo e acentuacao intactos")
    void despachaUmAviso() throws Exception {
        Long candidaturaId = gravarCandidatura("carlos", VAGA);
        enfileirar(candidaturaId, TipoEmail.CANDIDATURA_REPROVADA, 42, MOTIVO);

        assertThat(despachante.despacharUmLote()).isEqualTo(1);

        MimeMessage mensagem = unicaMensagem();
        assertThat(paraDe(mensagem)).isEqualTo("carlos.pereira@exemplo.com.br");
        assertThat(remetenteDe(mensagem)).isEqualTo("naoresponda@gupyclone.com.br");
        assertThat(assuntoDe(mensagem)).contains(VAGA);
        assertThat(corpoDe(mensagem))
                .contains("Carlos Pereira")
                .contains(VAGA)
                .contains(MOTIVO)
                .contains("42");

        EmailOutbox linha = emails
                .findByCandidaturaIdAndTipo(candidaturaId, TipoEmail.CANDIDATURA_REPROVADA)
                .orElseThrow();
        assertThat(linha.getStatus()).isEqualTo(StatusEmail.ENVIADO);
        assertThat(linha.getEnviadoEm()).isNotNull();
        assertThat(linha.getReivindicadoEm()).isNull();
        assertThat(linha.getUltimoErro()).isNull();
    }

    @Test
    @DisplayName("o aviso de aprovacao cita a vaga e a pontuacao")
    void despachaAprovacao() throws Exception {
        Long candidaturaId = gravarCandidatura("joao", VAGA);
        enfileirar(candidaturaId, TipoEmail.CANDIDATURA_APROVADA, 87, null);

        assertThat(despachante.despacharUmLote()).isEqualTo(1);

        MimeMessage mensagem = unicaMensagem();
        assertThat(paraDe(mensagem)).isEqualTo("joao.lima@exemplo.com.br");
        assertThat(assuntoDe(mensagem)).contains(VAGA);
        assertThat(corpoDe(mensagem))
                .contains("Joao Lima")
                .contains(VAGA)
                .contains("87");
    }

    @Test
    @DisplayName("enfileirar duas vezes manda um unico e-mail")
    void enfileirarDuasVezesMandaUmEmail() {
        Long candidaturaId = gravarCandidatura("carlos", VAGA);

        enfileirar(candidaturaId, TipoEmail.CANDIDATURA_REPROVADA, 42, MOTIVO);
        enfileirar(candidaturaId, TipoEmail.CANDIDATURA_REPROVADA, 42, MOTIVO);

        assertThat(emails.countByCandidaturaId(candidaturaId)).isEqualTo(1);
        assertThat(despachante.despacharUmLote()).isEqualTo(1);

        aguardarMensagens(1);
    }

    @Test
    @DisplayName("despachar duas vezes seguidas nao reenvia o que ja saiu")
    void segundoDespachoNaoReenvia() {
        Long candidaturaId = gravarCandidatura("carlos", VAGA);
        enfileirar(candidaturaId, TipoEmail.CANDIDATURA_APROVADA, 90, null);

        assertThat(despachante.despacharUmLote()).isEqualTo(1);
        aguardarMensagens(1);

        assertThat(despachante.despacharUmLote()).isZero();
        assertThat(smtp.getReceivedMessages()).hasSize(1);
        assertThat(emails.countByStatus(StatusEmail.ENVIADO)).isEqualTo(1);
    }

    @Test
    @DisplayName("varios workers ao mesmo tempo: nenhum aviso se perde e nenhum duplica")
    void variosWorkersNaoDuplicamNemPerdem() throws Exception {
        int quantidade = 12;
        int workers = 6;
        List<String> titulos = new ArrayList<>();
        for (int i = 1; i <= quantidade; i++) {
            String titulo = VAGA + " vaga " + i;
            titulos.add(titulo);
            // Alterna os candidatos porque a candidatura e unica por (vaga,
            // candidato); vagas distintas tambem dao a cada aviso um assunto
            // distinto, o que torna duplicata detectavel.
            Long candidaturaId = gravarCandidatura(i % 2 == 0 ? "carlos" : "joao", titulo);
            enfileirar(candidaturaId, TipoEmail.CANDIDATURA_APROVADA, 70 + i, null);
        }
        assertThat(emails.countByStatus(StatusEmail.PENDENTE)).isEqualTo(quantidade);

        CountDownLatch partida = new CountDownLatch(1);
        ExecutorService piscina = Executors.newFixedThreadPool(workers);
        List<Future<Integer>> resultados = new ArrayList<>();
        try {
            for (int i = 0; i < workers; i++) {
                resultados.add(piscina.submit(() -> {
                    partida.await();
                    int enviados = 0;
                    int rodada;
                    // Cada worker insiste ate nao reivindicar mais nada: quando
                    // o ultimo deles chega a zero, a fila esta vazia de fato.
                    do {
                        rodada = despachante.despacharUmLote();
                        enviados += rodada;
                    } while (rodada > 0);
                    return enviados;
                }));
            }
            partida.countDown();
            int total = 0;
            for (Future<Integer> resultado : resultados) {
                total += resultado.get(120, TimeUnit.SECONDS);
            }
            assertThat(total).as("cada aviso foi enviado por um unico worker").isEqualTo(quantidade);
        } finally {
            piscina.shutdownNow();
        }

        // Os workers terminaram: nada mais pode chegar, entao a contagem e final.
        MimeMessage[] recebidas = smtp.getReceivedMessages();
        assertThat(recebidas).hasSize(quantidade);

        List<String> assuntos = new ArrayList<>();
        for (MimeMessage mensagem : recebidas) {
            assuntos.add(assuntoDe(mensagem));
        }
        assertThat(assuntos).doesNotHaveDuplicates();
        assertThat(titulos).allSatisfy(titulo ->
                assertThat(assuntos).anySatisfy(assunto -> assertThat(assunto).contains(titulo)));

        assertThat(emails.countByStatus(StatusEmail.ENVIADO)).isEqualTo(quantidade);
        assertThat(emails.countByStatus(StatusEmail.PENDENTE)).isZero();
        assertThat(emails.countByStatus(StatusEmail.ENVIANDO)).isZero();
        assertThat(emails.countByStatus(StatusEmail.FALHO)).isZero();
    }

    @Test
    @DisplayName("com o SMTP fora do ar o aviso volta para a fila e sai no proximo despacho")
    void servidorForaDoArNaoPerdeOAviso() {
        Long candidaturaId = gravarCandidatura("carlos", VAGA);
        enfileirar(candidaturaId, TipoEmail.CANDIDATURA_REPROVADA, 10, MOTIVO);
        Long avisoId = idDoAviso(candidaturaId);

        smtp.stop();
        assertThat(despachante.despacharUmLote()).isZero();

        EmailOutbox depoisDaFalha = emails.findById(avisoId).orElseThrow();
        assertThat(depoisDaFalha.getStatus()).isEqualTo(StatusEmail.PENDENTE);
        assertThat(depoisDaFalha.getTentativas()).isEqualTo(1);
        assertThat(depoisDaFalha.getUltimoErro()).isNotBlank();
        assertThat(depoisDaFalha.getReivindicadoEm()).isNull();
        assertThat(depoisDaFalha.getEnviadoEm()).isNull();

        smtp.start();
        assertThat(despachante.despacharUmLote()).isEqualTo(1);

        aguardarMensagens(1);
        assertThat(emails.findById(avisoId).orElseThrow().getStatus()).isEqualTo(StatusEmail.ENVIADO);
    }

    @Test
    @DisplayName("esgotadas as tentativas o aviso vira FALHO e para de ser reivindicado")
    void tentativasEsgotadasViramFalho() {
        Long candidaturaId = gravarCandidatura("carlos", VAGA);
        enfileirar(candidaturaId, TipoEmail.CANDIDATURA_REPROVADA, 10, MOTIVO);
        Long avisoId = idDoAviso(candidaturaId);

        smtp.stop();
        for (int tentativa = 1; tentativa <= 3; tentativa++) {
            assertThat(despachante.despacharUmLote()).isZero();
        }

        EmailOutbox esgotado = emails.findById(avisoId).orElseThrow();
        assertThat(esgotado.getStatus()).isEqualTo(StatusEmail.FALHO);
        assertThat(esgotado.getTentativas()).isEqualTo(3);

        // FALHO sai da fila: nem o despacho nem a recuperacao de lease o tocam.
        assertThat(despachante.despacharUmLote()).isZero();
        assertThat(despachante.recuperarLeasesExpirados()).isZero();
        assertThat(emails.findById(avisoId).orElseThrow().getTentativas()).isEqualTo(3);
        assertThat(smtp.getReceivedMessages()).isEmpty();
    }

    @Test
    @DisplayName("lease expirado volta para PENDENTE e o aviso sai depois")
    void leaseExpiradoVoltaParaFila() {
        Long candidaturaId = gravarCandidatura("carlos", VAGA);
        enfileirar(candidaturaId, TipoEmail.CANDIDATURA_APROVADA, 90, null);
        Long avisoId = idDoAviso(candidaturaId);

        // O estado que um worker morto deixaria: reivindicado e nunca concluido.
        jdbc.sql("""
                update email_outbox
                   set status = 'ENVIANDO',
                       reivindicado_em = now() - interval '30 minutes',
                       tentativas = 1
                 where id = ?
                """).param(avisoId).update();

        assertThat(despachante.despacharUmLote()).as("ENVIANDO nao e reivindicado").isZero();

        assertThat(despachante.recuperarLeasesExpirados()).isEqualTo(1);

        EmailOutbox devolvido = emails.findById(avisoId).orElseThrow();
        assertThat(devolvido.getStatus()).isEqualTo(StatusEmail.PENDENTE);
        assertThat(devolvido.getReivindicadoEm()).isNull();

        assertThat(despachante.despacharUmLote()).isEqualTo(1);
        aguardarMensagens(1);
    }

    @Test
    @DisplayName("lease ainda valido nao e recuperado")
    void leaseValidoNaoEDevolvido() {
        Long candidaturaId = gravarCandidatura("carlos", VAGA);
        enfileirar(candidaturaId, TipoEmail.CANDIDATURA_APROVADA, 90, null);
        Long avisoId = idDoAviso(candidaturaId);

        jdbc.sql("""
                update email_outbox
                   set status = 'ENVIANDO',
                       reivindicado_em = now()
                 where id = ?
                """).param(avisoId).update();

        assertThat(despachante.recuperarLeasesExpirados()).isZero();
        assertThat(emails.findById(avisoId).orElseThrow().getStatus()).isEqualTo(StatusEmail.ENVIANDO);
    }

    private MimeMessage unicaMensagem() {
        aguardarMensagens(1);
        return smtp.getReceivedMessages()[0];
    }

    /**
     * O {@code send} so volta depois do 250 do servidor, entao em geral a
     * mensagem ja esta na caixa; a espera existe para nao depender disso.
     */
    private void aguardarMensagens(int quantidade) {
        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(smtp.getReceivedMessages()).hasSize(quantidade));
    }

    private Long idDoAviso(Long candidaturaId) {
        return emails.findAllByCandidaturaId(candidaturaId).getFirst().getId();
    }
}
