package com.gupyclone.candidaturas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gupyclone.curriculos.Curriculo;
import com.gupyclone.curriculos.CurriculoInvalidoException;
import com.gupyclone.curriculos.OrigemTexto;
import com.gupyclone.curriculos.RecebedorDeCurriculos;
import com.gupyclone.triagem.StatusJob;
import com.gupyclone.vagas.VagaInexistenteException;
import com.gupyclone.vagas.VagaRepository;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Prova das garantias da candidatura: ordem das etapas, "os dois juntos ou
 * nenhum" e o comportamento sob corrida de verdade, com threads e PostgreSQL
 * real decidindo quem ganha.
 */
class ServicoDeCandidaturasTest extends TesteDeCandidaturas {

    /** Folga generosa: cada tentativa abre conexao, le PDF e grava no banco. */
    private static final Duration PACIENCIA = Duration.ofSeconds(60);

    @Autowired
    private RecebedorDeCurriculos recebedor;

    @Autowired
    private VagaRepository vagas;

    @Autowired
    private TransactionTemplate transacao;

    @Test
    @DisplayName("a candidatura chega com vaga, candidato e curriculo legiveis fora da transacao")
    void candidaturaChegaComAssociacoesCarregadas() {
        Candidatura nova = servico.candidatar(carlos, vaga, "carlos.pdf", PDF_COM_TEXTO);

        // Sem transacao em volta do teste e com open-in-view falso: ler estas
        // associacoes so funciona se o servico as carregou antes de devolver.
        assertThat(nova.getVaga().getTitulo()).isEqualTo(TITULO_DA_VAGA);
        assertThat(nova.getCandidato().getNome()).isEqualTo("Carlos Pereira");
        assertThat(nova.getCurriculo().getOrigemTexto()).isEqualTo(OrigemTexto.CAMADA_DE_TEXTO);
        assertThat(nova.getStatus()).isEqualTo(StatusCandidatura.RECEBIDA);

        // As LISTAGENS vem por projecao, nao como entidade: nao ha associacao
        // preguicosa para resolver e, principalmente, o PDF em bytea e o texto
        // extraido nao sobem para a memoria so para exibir uma linha.
        CandidaturaResposta daLista = servico.doCandidato(carlos).getFirst();
        assertThat(daLista.tituloDaVaga()).isEqualTo(TITULO_DA_VAGA);
        assertThat(daLista.nomeDoCandidato()).isEqualTo("Carlos Pereira");
        assertThat(daLista.origemDoTexto()).isEqualTo(OrigemTexto.CAMADA_DE_TEXTO);
        assertThat(daLista.status()).isEqualTo(StatusCandidatura.RECEBIDA);
        assertThat(daLista.vagaId()).isEqualTo(vaga);
        assertThat(daLista.criadoEm()).isNotNull();

        CandidaturaResposta doRh = servico.daVaga(vaga, ana).getFirst();
        assertThat(doRh.nomeDoCandidato()).isEqualTo("Carlos Pereira");
        assertThat(doRh.origemDoTexto()).isEqualTo(OrigemTexto.CAMADA_DE_TEXTO);
        assertThat(doRh.candidatoId()).isEqualTo(carlos);
    }

    @Test
    @DisplayName("a segunda tentativa e recusada antes de gastar extracao de texto")
    void segundaTentativaNaoGastaExtracao() {
        servico.candidatar(carlos, vaga, "carlos.pdf", PDF_COM_TEXTO);

        assertThatThrownBy(() -> servico.candidatar(carlos, vaga, "carlos-de-novo.pdf", PDF_COM_TEXTO))
                .isInstanceOf(CandidaturaDuplicadaException.class);

        // A checagem rapida vem antes de receber o curriculo: se viesse depois,
        // existiria aqui um segundo curriculo (e um OCR gasto por nada).
        assertThat(curriculos.count()).isEqualTo(1);
        assertThat(candidaturas.count()).isEqualTo(1);
        assertThat(analises.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("vaga fechada e recusada antes de gastar extracao de texto")
    void vagaFechadaRecusaAntesDeExtrair() {
        servicoDeVagas.fechar(vaga, ana);

        assertThatThrownBy(() -> servico.candidatar(carlos, vaga, "carlos.pdf", PDF_COM_TEXTO))
                .isInstanceOf(VagaFechadaException.class);

        assertThat(curriculos.count()).isZero();
        assertThat(candidaturas.count()).isZero();
        assertThat(analises.count()).isZero();
    }

    @Test
    @DisplayName("vaga inexistente e recusada com o 404 de dominio da fatia de vagas")
    void vagaInexistenteRecusada() {
        assertThatThrownBy(() -> servico.candidatar(carlos, 9_999_999L, "carlos.pdf", PDF_COM_TEXTO))
                .isInstanceOf(VagaInexistenteException.class);

        assertThat(curriculos.count()).isZero();
    }

    @Test
    @DisplayName("arquivo ilegivel nao gera curriculo, candidatura nem job")
    void arquivoIlegivelNaoGravaNada() {
        assertThatThrownBy(() -> servico.candidatar(
                carlos, vaga, "carlos.pdf", "nao sou um pdf".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(CurriculoInvalidoException.class);

        assertThat(curriculos.count()).isZero();
        assertThat(candidaturas.count()).isZero();
        assertThat(analises.count()).isZero();
    }

    @Test
    @DisplayName("as candidaturas de uma vaga sao do RH que a publicou, e so dele")
    void candidaturasDaVagaSaoDoDono() {
        servico.candidatar(carlos, vaga, "carlos.pdf", PDF_COM_TEXTO);
        servico.candidatar(joao, vaga, "joao.pdf", PDF_COM_TEXTO);

        assertThat(servico.daVaga(vaga, ana)).hasSize(2);
        assertThatThrownBy(() -> servico.daVaga(vaga, bia)).isInstanceOf(AccessDeniedException.class);
        assertThat(servico.doCandidato(carlos)).hasSize(1);
        assertThat(servico.doCandidato(joao)).hasSize(1);
    }

    @Test
    @DisplayName("candidatar-se de dentro de uma transacao e recusado na hora")
    void candidatarDentroDeTransacaoEhRecusado() {
        // Dentro de uma transacao de quem chama, a extracao de texto ficaria
        // presa nela — e com ela a conexao do pool, por segundos de OCR. A
        // propagacao NEVER recusa esse chamador na primeira chamada, em
        // desenvolvimento, em vez de deixar o gargalo para producao.
        assertThatThrownBy(() -> transacao.executeWithoutResult(status ->
                servico.candidatar(carlos, vaga, "carlos.pdf", PDF_COM_TEXTO)))
                .isInstanceOf(IllegalTransactionStateException.class);

        assertThat(curriculos.count()).isZero();
        assertThat(candidaturas.count()).isZero();
        assertThat(analises.count()).isZero();
    }

    @Test
    @DisplayName("perder a corrida no indice unico vira candidatura duplicada, nao erro de banco")
    void perderACorridaNoIndiceUnicoViraCandidaturaDuplicada() {
        Curriculo doRival = recebedor.receber(carlos, "rival.pdf", PDF_COM_TEXTO);
        ExecutorService piscina = Executors.newSingleThreadExecutor();
        try {
            Future<Candidatura> emParalelo = transacao.execute(status -> {
                // Grava a candidatura rival e NAO comita: enquanto esta
                // transacao estiver aberta a linha e invisivel, portanto a
                // checagem rapida do servico nao tem como enxerga-la.
                candidaturas.saveAndFlush(new Candidatura(
                        vagas.findById(vaga).orElseThrow(),
                        candidatos.findById(carlos).orElseThrow(),
                        curriculos.findById(doRival.getId()).orElseThrow()));

                Future<Candidatura> tentativa = piscina.submit(
                        () -> servico.candidatar(carlos, vaga, "carlos.pdf", PDF_COM_TEXTO));

                // O segundo curriculo aparecer significa que o servico ja
                // passou da checagem rapida e so lhe resta o insert, que esta
                // bloqueado no indice unico esperando este commit.
                Awaitility.await().atMost(PACIENCIA).until(() -> curriculos.count() == 2);
                return tentativa;
            });

            assertThatThrownBy(() -> emParalelo.get(PACIENCIA.toSeconds(), TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(CandidaturaDuplicadaException.class);
        } finally {
            piscina.shutdownNow();
        }

        assertThat(candidaturas.count()).isEqualTo(1);
        // A transacao da gravacao caiu inteira: nem candidatura nem job de
        // quem perdeu. O curriculo, gravado antes dela e em transacao propria,
        // sobrevive — e a decisao documentada no servico.
        assertThat(analises.count()).isZero();
        assertThat(curriculos.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("10 pedidos simultaneos do mesmo candidato na mesma vaga: 1 candidatura, 1 job, 9 conflitos")
    void dezPedidosSimultaneosDoMesmoCandidato() {
        Resultado resultado = emParalelo(10, pedido -> servico
                .candidatar(carlos, vaga, "carlos-" + pedido + ".pdf", PDF_COM_TEXTO)
                .getId());

        assertThat(resultado.aceitas()).hasSize(1);
        // Nenhuma excecao de banco vazando: a corrida inteira chega a quem
        // chamou como candidatura duplicada, que a API traduz em 409.
        assertThat(resultado.recusas()).hasSize(9)
                .extracting(Object::getClass)
                .containsOnly(CandidaturaDuplicadaException.class);

        assertThat(candidaturas.count()).isEqualTo(1);
        assertThat(analises.count()).isEqualTo(1);
        assertThat(analises.findByCandidaturaId(resultado.aceitas().getFirst())).isPresent();
        assertThat(analises.findAllByStatus(StatusJob.PENDENTE)).hasSize(1);
    }

    @Test
    @DisplayName("10 candidatos diferentes na mesma vaga ao mesmo tempo: 10 candidaturas e 10 jobs")
    void dezCandidatosDiferentesAoMesmoTempo() {
        List<Long> concorrentes = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            concorrentes.add(candidatoExtra("concorrente-" + i, "Concorrente " + i));
        }

        Resultado resultado = emParalelo(concorrentes.size(), pedido -> servico
                .candidatar(concorrentes.get(pedido), vaga, "concorrente-" + pedido + ".pdf", PDF_COM_TEXTO)
                .getId());

        assertThat(resultado.recusas()).isEmpty();
        assertThat(resultado.aceitas()).hasSize(10).doesNotHaveDuplicates();
        assertThat(candidaturas.count()).isEqualTo(10);
        assertThat(analises.count()).isEqualTo(10);
        assertThat(analises.findAllByStatus(StatusJob.PENDENTE)).hasSize(10);
        assertThat(resultado.aceitas())
                .allSatisfy(id -> assertThat(analises.findByCandidaturaId(id)).isPresent());
    }

    /** O que sobrou de uma rodada concorrente: ids aceitos e falhas recebidas. */
    private record Resultado(List<Long> aceitas, List<Throwable> recusas) {
    }

    /** Um pedido de candidatura, identificado pelo seu numero na rodada. */
    private interface Pedido {
        Long executar(int numero) throws Exception;
    }

    /**
     * Dispara {@code quantos} pedidos de verdade ao mesmo tempo.
     *
     * <p>O {@code CountDownLatch} e o que faz disso uma corrida: as threads sao
     * criadas antes e ficam presas na largada, em vez de comecarem espacadas
     * pelo tempo de criacao de cada uma.
     */
    private Resultado emParalelo(int quantos, Pedido pedido) {
        ExecutorService piscina = Executors.newFixedThreadPool(quantos);
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<Long>> tentativas = new ArrayList<>();
        try {
            for (int numero = 0; numero < quantos; numero++) {
                int meuNumero = numero;
                tentativas.add(piscina.submit(() -> {
                    largada.await();
                    return pedido.executar(meuNumero);
                }));
            }
            largada.countDown();

            List<Long> aceitas = new ArrayList<>();
            List<Throwable> recusas = new ArrayList<>();
            for (Future<Long> tentativa : tentativas) {
                try {
                    aceitas.add(tentativa.get(PACIENCIA.toSeconds(), TimeUnit.SECONDS));
                } catch (ExecutionException falha) {
                    recusas.add(falha.getCause());
                } catch (InterruptedException | TimeoutException falha) {
                    throw new IllegalStateException("pedido nao terminou dentro da paciencia", falha);
                }
            }
            return new Resultado(aceitas, recusas);
        } finally {
            piscina.shutdownNow();
        }
    }
}
