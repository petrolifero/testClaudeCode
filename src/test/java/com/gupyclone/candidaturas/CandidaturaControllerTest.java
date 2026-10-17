package com.gupyclone.candidaturas;

import static org.assertj.core.api.Assertions.assertThat;

import com.gupyclone.curriculos.Curriculo;
import com.gupyclone.curriculos.OrigemTexto;
import com.gupyclone.suporte.FabricaDePdf;
import com.gupyclone.triagem.AnaliseJob;
import com.gupyclone.triagem.StatusJob;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * Prova da candidatura pela API: HTTP real, multipart real, PostgreSQL real e
 * PDFs reais (inclusive um que so pode ser lido por OCR).
 */
class CandidaturaControllerTest extends TesteDeCandidaturas {

    @Test
    @DisplayName("Carlos se candidata com PDF de texto e fica com exatamente um job de analise pendente")
    void carlosSeCandidataComPdfDeTexto() {
        candidatar("carlos", vaga, "carlos-pereira.pdf", PDF_COM_TEXTO)
                .expectStatus().isCreated()
                .expectHeader().valueMatches(HttpHeaders.LOCATION, "/api/candidaturas/\\d+")
                .expectBody()
                .jsonPath("$.id").isNumber()
                .jsonPath("$.vagaId").isEqualTo(vaga.intValue())
                .jsonPath("$.tituloDaVaga").isEqualTo(TITULO_DA_VAGA)
                .jsonPath("$.candidatoId").isEqualTo(carlos.intValue())
                .jsonPath("$.nomeDoCandidato").isEqualTo("Carlos Pereira")
                .jsonPath("$.status").isEqualTo("RECEBIDA")
                .jsonPath("$.origemDoTexto").isEqualTo("CAMADA_DE_TEXTO")
                .jsonPath("$.pontuacao").isEmpty()
                .jsonPath("$.motivo").isEmpty()
                .jsonPath("$.analisadaEm").isEmpty()
                .jsonPath("$.criadoEm").isNotEmpty();

        assertThat(candidaturas.count()).isEqualTo(1);
        Candidatura gravada = candidaturas.findAll().getFirst();
        assertThat(gravada.getStatus()).isEqualTo(StatusCandidatura.RECEBIDA);
        // O nome chegou pelo Content-Disposition da parte, e nao do corpo:
        // e a prova de que o multipart foi enviado como arquivo.
        assertThat(curriculos.findAllByCandidatoIdOrderByCriadoEmDesc(carlos))
                .singleElement()
                .extracting(Curriculo::getNomeArquivo)
                .isEqualTo("carlos-pereira.pdf");

        // "Se a candidatura existe, o job de analise existe, e existe exatamente um."
        assertThat(analises.count()).isEqualTo(1);
        AnaliseJob job = analises.findByCandidaturaId(gravada.getId()).orElseThrow();
        assertThat(job.getStatus()).isEqualTo(StatusJob.PENDENTE);
        assertThat(job.getTentativas()).isZero();
    }

    @Test
    @DisplayName("Carlos se candidata com PDF que e so imagem e o curriculo fica com origem OCR")
    void carlosSeCandidataComPdfSomenteImagem() {
        byte[] digitalizado = FabricaDePdf.somenteImagem(
                "Carlos Pereira",
                "Desenvolvedor Java com Spring Boot",
                "Experiencia com PostgreSQL");

        candidatar("carlos", vaga, "carlos-digitalizado.pdf", digitalizado)
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.status").isEqualTo("RECEBIDA")
                .jsonPath("$.origemDoTexto").isEqualTo("OCR");

        assertThat(curriculos.findAllByCandidatoIdOrderByCriadoEmDesc(carlos))
                .singleElement()
                .satisfies(curriculo -> {
                    assertThat(curriculo.getOrigemTexto()).isEqualTo(OrigemTexto.OCR);
                    assertThat(curriculo.getTexto().toLowerCase()).contains("java");
                });
        assertThat(analises.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("candidatar-se duas vezes na mesma vaga da 409 e deixa uma candidatura e um job")
    void duasVezesNaMesmaVagaDa409() {
        candidatar("carlos", vaga, "carlos.pdf", PDF_COM_TEXTO).expectStatus().isCreated();

        candidatar("carlos", vaga, "carlos-de-novo.pdf", PDF_COM_TEXTO)
                .expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.codigo").isEqualTo("candidatura_duplicada");

        assertThat(candidaturas.count()).isEqualTo(1);
        assertThat(analises.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("vaga fechada nao recebe candidatura: 409 e nada gravado")
    void vagaFechadaDa409() {
        servicoDeVagas.fechar(vaga, ana);

        candidatar("carlos", vaga, "carlos.pdf", PDF_COM_TEXTO)
                .expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.codigo").isEqualTo("vaga_fechada");

        assertThat(candidaturas.count()).isZero();
        assertThat(analises.count()).isZero();
        assertThat(curriculos.count()).isZero();
    }

    @Test
    @DisplayName("arquivo que nao e PDF da 422 e nao grava curriculo, candidatura nem job")
    void arquivoQueNaoEhPdfDa422() {
        candidatar("carlos", vaga, "curriculo.pdf", "nao sou um pdf".getBytes(StandardCharsets.UTF_8))
                .expectStatus().isEqualTo(422)
                .expectBody().jsonPath("$.codigo").isEqualTo("curriculo_invalido");

        assertThat(curriculos.count()).isZero();
        assertThat(candidaturas.count()).isZero();
        assertThat(analises.count()).isZero();
    }

    @Test
    @DisplayName("arquivo vazio da 422")
    void arquivoVazioDa422() {
        candidatar("carlos", vaga, "vazio.pdf", new byte[0])
                .expectStatus().isEqualTo(422)
                .expectBody().jsonPath("$.codigo").isEqualTo("curriculo_invalido");

        assertThat(curriculos.count()).isZero();
        assertThat(candidaturas.count()).isZero();
    }

    @Test
    @DisplayName("requisicao multipart sem a parte arquivo da 422")
    void parteArquivoAusenteDa422() {
        MultiValueMap<String, Object> semArquivo = new LinkedMultiValueMap<>();
        semArquivo.add("observacao", "esqueci de anexar o currículo");

        clienteComo("carlos").post().uri("/api/vagas/" + vaga + "/candidaturas")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(semArquivo)
                .exchange()
                .expectStatus().isEqualTo(422)
                .expectBody().jsonPath("$.codigo").isEqualTo("curriculo_invalido");

        assertThat(curriculos.count()).isZero();
        assertThat(candidaturas.count()).isZero();
    }

    @Test
    @DisplayName("Ana, do RH, nao se candidata")
    void rhNaoSeCandidata() {
        candidatar("ana", vaga, "ana.pdf", PDF_COM_TEXTO).expectStatus().isForbidden();

        assertThat(candidaturas.count()).isZero();
        assertThat(curriculos.count()).isZero();
    }

    @Test
    @DisplayName("deslogado nao se candidata nem lista candidatura alguma")
    void anonimoNaoSeCandidata() {
        clienteAnonimo().post().uri("/api/vagas/" + vaga + "/candidaturas")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(multipart("carlos.pdf", PDF_COM_TEXTO))
                .exchange()
                .expectStatus().isUnauthorized();

        clienteAnonimo().get().uri("/api/candidaturas/minhas").exchange().expectStatus().isUnauthorized();
        clienteAnonimo().get().uri("/api/vagas/" + vaga + "/candidaturas").exchange()
                .expectStatus().isUnauthorized();

        assertThat(candidaturas.count()).isZero();
    }

    @Test
    @DisplayName("cada candidato ve somente as proprias candidaturas")
    void candidatoVeSomenteAsProprias() {
        candidatar("carlos", vaga, "carlos.pdf", PDF_COM_TEXTO).expectStatus().isCreated();

        clienteComo("carlos").get().uri("/api/candidaturas/minhas").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(1)
                .jsonPath("$[0].candidatoId").isEqualTo(carlos.intValue())
                .jsonPath("$[0].tituloDaVaga").isEqualTo(TITULO_DA_VAGA);

        clienteComo("joao").get().uri("/api/candidaturas/minhas").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.length()").isEqualTo(0);
    }

    @Test
    @DisplayName("o RH dono da vaga ve as candidaturas dela; outro RH recebe 403")
    void somenteODonoDaVagaVeAsCandidaturas() {
        candidatar("carlos", vaga, "carlos.pdf", PDF_COM_TEXTO).expectStatus().isCreated();

        clienteComo("ana").get().uri("/api/vagas/" + vaga + "/candidaturas").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(1)
                .jsonPath("$[0].nomeDoCandidato").isEqualTo("Carlos Pereira")
                .jsonPath("$[0].status").isEqualTo("RECEBIDA")
                .jsonPath("$[0].origemDoTexto").isEqualTo("CAMADA_DE_TEXTO");

        clienteComo("bia").get().uri("/api/vagas/" + vaga + "/candidaturas").exchange()
                .expectStatus().isForbidden();
    }

    @Test
    @DisplayName("candidatar-se a vaga inexistente da 404")
    void vagaInexistenteDa404() {
        candidatar("carlos", 9_999_999L, "carlos.pdf", PDF_COM_TEXTO)
                .expectStatus().isNotFound()
                .expectBody().jsonPath("$.codigo").isEqualTo("vaga_inexistente");

        assertThat(curriculos.count()).isZero();
    }

    private RestTestClient.ResponseSpec candidatar(String persona, Long vagaId, String nome, byte[] pdf) {
        return clienteComo(persona).post().uri("/api/vagas/" + vagaId + "/candidaturas")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(multipart(nome, pdf))
                .exchange();
    }

    /**
     * Corpo multipart com a parte "arquivo" enviada como arquivo.
     *
     * <p>O {@code getFilename()} sobrescrito e o que faz o Spring escrever
     * {@code filename} no {@code Content-Disposition}: sem ele a parte chega ao
     * servidor como campo de formulario, e nao como arquivo.
     */
    private static MultiValueMap<String, Object> multipart(String nomeDoArquivo, byte[] conteudo) {
        MultiValueMap<String, Object> corpo = new LinkedMultiValueMap<>();
        corpo.add("arquivo", new ByteArrayResource(conteudo) {
            @Override
            public String getFilename() {
                return nomeDoArquivo;
            }
        });
        return corpo;
    }
}
