package com.gupyclone.triagem;

import static org.assertj.core.api.Assertions.assertThat;

import com.gupyclone.candidaturas.Candidatura;
import com.gupyclone.candidaturas.CandidaturaResposta;
import com.gupyclone.candidaturas.Candidaturas;
import com.gupyclone.candidaturas.StatusCandidatura;
import com.gupyclone.curriculos.OrigemTexto;
import com.gupyclone.notificacoes.AvisoAoCandidato;
import com.gupyclone.notificacoes.EmailOutbox;
import com.gupyclone.notificacoes.FilaDeEmails;
import com.gupyclone.notificacoes.TipoEmail;
import com.gupyclone.suporte.FabricaDePdf;
import com.gupyclone.vagas.ServicoDeVagas.NovoCriterio;
import com.gupyclone.vagas.Vaga;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Prova da triagem de ponta a ponta e das suas duas garantias.
 *
 * <p><strong>Nao perder nenhuma</strong>: todo job reivindicado termina com a
 * candidatura em status final, e um job cujo worker morreu volta pela expiracao
 * do lease. <strong>Nao duplicar</strong>: a conclusao e um
 * {@code update ... where status = 'RECEBIDA'} e so quem recebe 1 enfileira o
 * e-mail, entao reprocessar e inofensivo e cada candidatura rende exatamente um
 * aviso — inclusive com varios workers na mesma fila.
 */
class ProcessadorDeAnalisesTest extends TesteDeTriagem {

    /** Folga generosa: cada lote abre conexao, consulta tsvector e grava. */
    private static final long PACIENCIA_EM_SEGUNDOS = 120;

    @Autowired
    private ProcessadorDeAnalises processador;

    @Autowired
    private ApplicationContext contexto;

    @Autowired
    private FilaDeEmails fila;

    @Autowired
    private Candidaturas candidaturasPorId;

    @Autowired
    private TransactionTemplate transacao;

    @Test
    @DisplayName("sem gupy.agendamento.ativo o agendador nem chega a existir")
    void agendadorDesligadoNaSuite() {
        // E o que torna confiaveis as contagens exatas desta classe: ninguem
        // esta drenando a fila por tras das asserticoes.
        assertThat(contexto.getBeanNamesForType(AgendadorDeTriagem.class)).isEmpty();
    }

    @Test
    @DisplayName("fila vazia nao da trabalho nem erro")
    void filaVazia() {
        assertThat(processador.processarUmLote()).isZero();
        assertThat(processador.recuperarLeasesExpirados()).isZero();
    }

    @Test
    @DisplayName("fim a fim aprovada: candidatura APROVADA, job CONCLUIDO e um unico e-mail de aprovacao")
    void fimAFimAprovada() {
        Vaga vaga = publicarVagaDeExemplo(60);
        CandidaturaResposta candidatura = candidatar(carlos, vaga.getId(), PDF_QUE_ATENDE);

        assertThat(processador.processarUmLote()).isEqualTo(1);

        Candidatura analisada = candidaturas.findById(candidatura.id()).orElseThrow();
        assertThat(analisada.getStatus()).isEqualTo(StatusCandidatura.APROVADA);
        assertThat(analisada.getPontuacao()).isEqualTo(100);
        assertThat(analisada.getAnalisadaEm()).isNotNull();
        assertThat(analisada.getMotivo()).contains("Java");

        AnaliseJob job = analises.findByCandidaturaId(candidatura.id()).orElseThrow();
        assertThat(job.getStatus()).isEqualTo(StatusJob.CONCLUIDO);
        assertThat(job.getReivindicadoEm()).isNull();
        assertThat(job.getUltimoErro()).isNull();

        assertThat(emails.count()).isEqualTo(1);
        EmailOutbox aviso = emails.findAllByCandidaturaId(candidatura.id()).getFirst();
        assertThat(aviso.getTipo()).isEqualTo(TipoEmail.CANDIDATURA_APROVADA);
        assertThat(aviso.getDestinatario()).isEqualTo("carlos.pereira@exemplo.com.br");
        // O e-mail sai da transacao que concluiu a candidatura, com nome, vaga
        // e pontuacao que o processador leu antes de analisar: o redator nao
        // volta ao banco, entao e aqui que se ve se o aviso chegou completo.
        assertThat(aviso.getCorpo()).contains("Carlos Pereira").contains(TITULO_DA_VAGA).contains("100");
    }

    @Test
    @DisplayName("fim a fim reprovada: motivo explica o que faltou e sai um unico e-mail de reprovacao")
    void fimAFimReprovada() {
        Vaga vaga = publicarVagaDeExemplo(60);
        CandidaturaResposta candidatura = candidatar(carlos, vaga.getId(), PDF_QUE_NAO_ATENDE);

        assertThat(processador.processarUmLote()).isEqualTo(1);

        Candidatura analisada = candidaturas.findById(candidatura.id()).orElseThrow();
        assertThat(analisada.getStatus()).isEqualTo(StatusCandidatura.REPROVADA);
        assertThat(analisada.getPontuacao()).isZero();
        assertThat(analisada.getAnalisadaEm()).isNotNull();
        assertThat(analisada.getMotivo()).contains("não atendidos").contains("Java");

        assertThat(emails.count()).isEqualTo(1);
        EmailOutbox aviso = emails.findAllByCandidaturaId(candidatura.id()).getFirst();
        assertThat(aviso.getTipo()).isEqualTo(TipoEmail.CANDIDATURA_REPROVADA);
        assertThat(aviso.getCorpo()).contains("não atendidos").contains("Java");
    }

    @Test
    @DisplayName("criterio obrigatorio nao atendido reprova mesmo com pontuacao alta")
    void obrigatorioNaoAtendidoReprova() {
        Vaga vaga = publicarVaga("Pessoa de Dados", 50,
                new NovoCriterio("Python", "python", 90, false),
                new NovoCriterio("Certificação AWS", "aws", 10, true));
        CandidaturaResposta candidatura = candidatar(carlos, vaga.getId(), FabricaDePdf.comCamadaDeTexto(
                "Carlos Pereira",
                "Engenheiro de dados com Python, Pandas e Airflow",
                "Experiencia com data warehouse on premise"));

        assertThat(processador.processarUmLote()).isEqualTo(1);

        Candidatura analisada = candidaturas.findById(candidatura.id()).orElseThrow();
        assertThat(analisada.getPontuacao()).isEqualTo(90);
        assertThat(analisada.getStatus()).isEqualTo(StatusCandidatura.REPROVADA);
        assertThat(analisada.getMotivo()).contains("Certificação AWS").contains("obrigatório");
        assertThat(emails.findAllByCandidaturaId(candidatura.id()).getFirst().getTipo())
                .isEqualTo(TipoEmail.CANDIDATURA_REPROVADA);
    }

    @Test
    @DisplayName("vaga sem criterio nenhum aprova e avisa o candidato")
    void vagaSemCriterioAprova() {
        Vaga vaga = publicarVaga("Pessoa Generalista", 0);
        CandidaturaResposta candidatura = candidatar(carlos, vaga.getId(), PDF_QUE_NAO_ATENDE);

        assertThat(processador.processarUmLote()).isEqualTo(1);

        Candidatura analisada = candidaturas.findById(candidatura.id()).orElseThrow();
        assertThat(analisada.getStatus()).isEqualTo(StatusCandidatura.APROVADA);
        assertThat(analisada.getPontuacao()).isEqualTo(100);
        assertThat(analisada.getMotivo()).contains("não definiu critérios");
        assertThat(emails.findAllByCandidaturaId(candidatura.id()).getFirst().getTipo())
                .isEqualTo(TipoEmail.CANDIDATURA_APROVADA);
    }

    @Test
    @DisplayName("curriculo que so existe como imagem e analisado: o OCR chega inteiro ate a decisao")
    void curriculoDeOcrTambemEAnalisado() {
        Vaga vaga = publicarVaga("Pessoa Desenvolvedora Java", 40,
                new NovoCriterio("Java", "java", 60, true),
                new NovoCriterio("Spring", "spring", 40, false));
        CandidaturaResposta candidatura = candidatar(joao, vaga.getId(), FabricaDePdf.somenteImagem(
                "Joao Lima",
                "Desenvolvedor Java com Spring Boot",
                "Experiencia com PostgreSQL"));

        // A candidatura devolvida pelo servico ja diz de onde veio o texto.
        assertThat(candidatura.origemDoTexto()).isEqualTo(OrigemTexto.OCR);

        assertThat(processador.processarUmLote()).isEqualTo(1);

        Candidatura analisada = candidaturas.findById(candidatura.id()).orElseThrow();
        assertThat(analisada.getStatus()).isEqualTo(StatusCandidatura.APROVADA);
        assertThat(analisada.getPontuacao()).isGreaterThanOrEqualTo(60);
        assertThat(emails.countByCandidaturaId(candidatura.id())).isEqualTo(1);
    }

    @Test
    @DisplayName("processar duas vezes nao muda nada e nao manda um segundo e-mail")
    void processarDuasVezesNaoDuplica() {
        Vaga vaga = publicarVagaDeExemplo(60);
        CandidaturaResposta candidatura = candidatar(carlos, vaga.getId(), PDF_QUE_ATENDE);

        assertThat(processador.processarUmLote()).isEqualTo(1);
        OffsetDateTime primeiraAnalise = candidaturas.findById(candidatura.id()).orElseThrow().getAnalisadaEm();

        assertThat(processador.processarUmLote()).as("o job concluido nao e reivindicado de novo").isZero();

        Candidatura depois = candidaturas.findById(candidatura.id()).orElseThrow();
        assertThat(depois.getStatus()).isEqualTo(StatusCandidatura.APROVADA);
        assertThat(depois.getAnalisadaEm()).isEqualTo(primeiraAnalise);
        assertThat(emails.count()).isEqualTo(1);
        assertThat(analises.countByStatus(StatusJob.CONCLUIDO)).isEqualTo(1);
    }

    @Test
    @DisplayName("candidatura ja concluida: o job fecha sem reescrever nada e sem segundo e-mail")
    void candidaturaJaConcluidaNaoRecebeSegundoEmail() {
        Vaga vaga = publicarVagaDeExemplo(60);
        CandidaturaResposta candidatura = candidatar(carlos, vaga.getId(), PDF_QUE_ATENDE);
        // O estado que um worker morto entre as duas escritas deixaria: a
        // candidatura decidida e o job ainda na fila.
        jdbc.sql("""
                update candidatura
                   set status = 'REPROVADA',
                       pontuacao = 11,
                       motivo = 'decidida numa passada anterior',
                       analisada_em = now()
                 where id = ?
                """).param(candidatura.id()).update();

        assertThat(processador.processarUmLote()).isZero();

        Candidatura intocada = candidaturas.findById(candidatura.id()).orElseThrow();
        assertThat(intocada.getStatus()).isEqualTo(StatusCandidatura.REPROVADA);
        assertThat(intocada.getPontuacao()).isEqualTo(11);
        assertThat(intocada.getMotivo()).isEqualTo("decidida numa passada anterior");
        assertThat(emails.count()).as("o e-mail da passada anterior nao e refeito").isZero();
        assertThat(analises.findByCandidaturaId(candidatura.id()).orElseThrow().getStatus())
                .isEqualTo(StatusJob.CONCLUIDO);
    }

    @Test
    @DisplayName("lease expirado volta para PENDENTE e o job e concluido na passada seguinte")
    void leaseExpiradoNaoPerdeACandidatura() {
        Vaga vaga = publicarVagaDeExemplo(60);
        CandidaturaResposta candidatura = candidatar(carlos, vaga.getId(), PDF_QUE_ATENDE);
        Long jobId = analises.findByCandidaturaId(candidatura.id()).orElseThrow().getId();

        // O estado que um worker morto deixaria: reivindicado e nunca concluido.
        jdbc.sql("""
                update analise_job
                   set status = 'PROCESSANDO',
                       reivindicado_em = now() - interval '30 minutes',
                       tentativas = 1
                 where id = ?
                """).param(jobId).update();

        assertThat(processador.processarUmLote()).as("PROCESSANDO nao e reivindicado").isZero();
        assertThat(candidaturas.findById(candidatura.id()).orElseThrow().getStatus())
                .isEqualTo(StatusCandidatura.RECEBIDA);

        assertThat(processador.recuperarLeasesExpirados()).isEqualTo(1);

        AnaliseJob devolvido = analises.findById(jobId).orElseThrow();
        assertThat(devolvido.getStatus()).isEqualTo(StatusJob.PENDENTE);
        assertThat(devolvido.getReivindicadoEm()).isNull();

        assertThat(processador.processarUmLote()).isEqualTo(1);
        assertThat(candidaturas.findById(candidatura.id()).orElseThrow().getStatus())
                .isEqualTo(StatusCandidatura.APROVADA);
        assertThat(analises.findById(jobId).orElseThrow().getStatus()).isEqualTo(StatusJob.CONCLUIDO);
        assertThat(emails.countByCandidaturaId(candidatura.id())).isEqualTo(1);
    }

    @Test
    @DisplayName("lease ainda valido nao e recuperado")
    void leaseValidoNaoEDevolvido() {
        Vaga vaga = publicarVagaDeExemplo(60);
        CandidaturaResposta candidatura = candidatar(carlos, vaga.getId(), PDF_QUE_ATENDE);
        Long jobId = analises.findByCandidaturaId(candidatura.id()).orElseThrow().getId();
        jdbc.sql("update analise_job set status = 'PROCESSANDO', reivindicado_em = now() where id = ?")
                .param(jobId).update();

        assertThat(processador.recuperarLeasesExpirados()).isZero();
        assertThat(analises.findById(jobId).orElseThrow().getStatus()).isEqualTo(StatusJob.PROCESSANDO);
    }

    @Test
    @DisplayName("perder a corrida da conclusao nao reescreve o veredito nem manda segundo e-mail")
    void perderACorridaDaConclusaoNaoDuplica() throws Exception {
        Vaga vaga = publicarVagaDeExemplo(60);
        CandidaturaResposta candidatura = candidatar(carlos, vaga.getId(), PDF_QUE_ATENDE);
        ExecutorService piscina = Executors.newSingleThreadExecutor();
        try {
            Future<Integer> emParalelo = transacao.execute(status -> {
                // Concluo a candidatura como um worker rival faria, e NAO
                // comito: a linha fica travada, e quem le enxerga a versao
                // antiga (RECEBIDA). O processador vai ate o fim da analise e
                // so entao descobre a corrida.
                assertThat(concluirComoRival(candidatura.id())).isTrue();

                Future<Integer> tentativa = piscina.submit(processador::processarUmLote);
                Awaitility.await().atMost(Duration.ofSeconds(PACIENCIA_EM_SEGUNDOS))
                        .until(() -> bloqueadoNaTravaDaCandidatura() && analises.countByStatus(StatusJob.PENDENTE) == 0);
                return tentativa;
            });

            // Com o commit do rival, o update do processador destrava, reavalia
            // o "where status = 'RECEBIDA'" na versao nova e nao afeta linha
            // alguma. E esse 0 que impede o segundo e-mail.
            assertThat(esperar(emParalelo)).isZero();
        } finally {
            piscina.shutdownNow();
        }

        Candidatura doRival = candidaturas.findById(candidatura.id()).orElseThrow();
        assertThat(doRival.getStatus()).isEqualTo(StatusCandidatura.REPROVADA);
        assertThat(doRival.getPontuacao()).isEqualTo(7);
        assertThat(doRival.getMotivo()).isEqualTo("decidida pelo worker rival");
        assertThat(emails.count()).isEqualTo(1);
        assertThat(emails.findAllByCandidaturaId(candidatura.id()).getFirst().getTipo())
                .isEqualTo(TipoEmail.CANDIDATURA_REPROVADA);
        assertThat(analises.findByCandidaturaId(candidatura.id()).orElseThrow().getStatus())
                .isEqualTo(StatusJob.CONCLUIDO);
    }

    @Test
    @DisplayName("12 candidaturas e 4 workers ao mesmo tempo: nenhuma perdida, nenhum e-mail duplicado")
    void dozeCandidaturasComQuatroWorkers() throws Exception {
        int quantidade = 12;
        int workers = 4;
        Vaga vaga = publicarVagaDeExemplo(60);
        List<Long> candidaturaIds = new ArrayList<>();
        for (int numero = 0; numero < quantidade; numero++) {
            Long candidato = candidatoExtra("triagem-" + numero, "Pessoa de Triagem " + numero);
            // Metade atende e metade nao: os dois caminhos de decisao, e os
            // dois tipos de e-mail, acontecem ao mesmo tempo.
            byte[] pdf = numero % 2 == 0 ? PDF_QUE_ATENDE : PDF_QUE_NAO_ATENDE;
            candidaturaIds.add(candidatar(candidato, vaga.getId(), pdf).id());
        }
        assertThat(analises.countByStatus(StatusJob.PENDENTE)).isEqualTo(quantidade);

        CountDownLatch largada = new CountDownLatch(1);
        ExecutorService piscina = Executors.newFixedThreadPool(workers);
        List<Future<Integer>> rodadas = new ArrayList<>();
        int total = 0;
        try {
            for (int worker = 0; worker < workers; worker++) {
                rodadas.add(piscina.submit(() -> {
                    largada.await();
                    int concluidos = 0;
                    int rodada;
                    // Cada worker insiste ate nao reivindicar mais nada: quando
                    // o ultimo chega a zero, a fila esta vazia de fato.
                    do {
                        rodada = processador.processarUmLote();
                        concluidos += rodada;
                    } while (rodada > 0);
                    return concluidos;
                }));
            }
            largada.countDown();
            for (Future<Integer> rodada : rodadas) {
                total += esperar(rodada);
            }
        } finally {
            piscina.shutdownNow();
        }

        assertThat(total).as("cada candidatura foi concluida por um unico worker").isEqualTo(quantidade);

        // Nenhuma perdida: ninguem ficou em RECEBIDA nem sem data de analise.
        assertThat(candidaturas.findAllByStatus(StatusCandidatura.RECEBIDA)).isEmpty();
        assertThat(candidaturas.count()).isEqualTo(quantidade);
        assertThat(candidaturaIds).allSatisfy(id -> {
            Candidatura analisada = candidaturas.findById(id).orElseThrow();
            assertThat(analisada.getStatus().ehFinal()).isTrue();
            assertThat(analisada.getAnalisadaEm()).isNotNull();
            assertThat(analisada.getPontuacao()).isNotNull();
            assertThat(analisada.getMotivo()).isNotBlank();
            // Nao duplicar, candidatura por candidatura e nao so no total.
            assertThat(emails.countByCandidaturaId(id)).isEqualTo(1);
        });

        assertThat(analises.countByStatus(StatusJob.CONCLUIDO)).isEqualTo(quantidade);
        assertThat(analises.countByStatus(StatusJob.PENDENTE)).isZero();
        assertThat(analises.countByStatus(StatusJob.PROCESSANDO)).isZero();
        assertThat(analises.countByStatus(StatusJob.FALHO)).isZero();

        assertThat(emails.count()).isEqualTo(quantidade);
        assertThat(candidaturas.findAllByStatus(StatusCandidatura.APROVADA)).hasSize(quantidade / 2);
        assertThat(candidaturas.findAllByStatus(StatusCandidatura.REPROVADA)).hasSize(quantidade / 2);
    }

    /** Conclui e avisa como o processador faz, para a corrida ser a de verdade. */
    private boolean concluirComoRival(Long candidaturaId) {
        boolean concluida = candidaturasPorId.concluir(candidaturaId, StatusCandidatura.REPROVADA, 7,
                "decidida pelo worker rival");
        fila.enfileirar(new AvisoAoCandidato(candidaturaId, TipoEmail.CANDIDATURA_REPROVADA,
                "carlos.pereira@exemplo.com.br", "Carlos Pereira", TITULO_DA_VAGA, 7,
                "decidida pelo worker rival"));
        return concluida;
    }

    /**
     * O processador esta parado esperando a trava da linha da candidatura.
     *
     * <p>E o que torna a corrida deterministica em vez de uma aposta no
     * escalonador: so se comita depois de ver, no proprio PostgreSQL, que o
     * outro update esta bloqueado.
     */
    private boolean bloqueadoNaTravaDaCandidatura() {
        Integer bloqueados = jdbc.sql("""
                select count(*)
                  from pg_stat_activity
                 where wait_event_type = 'Lock'
                   and query ilike '%update%candidatura%'
                """).query(Integer.class).single();
        return bloqueados != null && bloqueados >= 1;
    }

    private static int esperar(Future<Integer> rodada) throws ExecutionException, InterruptedException {
        try {
            return rodada.get(PACIENCIA_EM_SEGUNDOS, TimeUnit.SECONDS);
        } catch (TimeoutException naoTerminou) {
            throw new IllegalStateException("worker nao terminou dentro da paciencia", naoTerminou);
        }
    }
}
