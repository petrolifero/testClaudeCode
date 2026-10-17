package com.gupyclone.curriculos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gupyclone.config.PropriedadesOcr;
import com.gupyclone.suporte.FabricaDePdf;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Prova da parte mais arriscada do projeto: ler currículo tanto do PDF com
 * texto selecionável quanto daquele em que a página é só imagem.
 *
 * <p>Sem Spring e sem banco de proposito — o componente e montado a mao —, mas
 * com PDFs de verdade e com o Tesseract de verdade: o OCR aqui roda o binario
 * instalado na maquina, nao um dublê.
 */
class ExtratorDeTextoDePdfTest {

    private static final PropriedadesOcr PROPRIEDADES = new PropriedadesOcr(
            System.getenv().getOrDefault("GUPY_OCR_IDIOMAS", "por+eng"),
            System.getenv().getOrDefault("GUPY_OCR_DATAPATH", "/usr/share/tesseract-ocr/5/tessdata"),
            300,
            40);

    private final ExtratorDeTextoDePdf extrator =
            new ExtratorDeTextoComOcrDeFallback(new MotorDeOcrTesseract(PROPRIEDADES), PROPRIEDADES);

    @Test
    @DisplayName("PDF com camada de texto e lido sem OCR")
    void camadaDeTexto() {
        byte[] pdf = FabricaDePdf.comCamadaDeTexto(
                "Carlos Pereira",
                "Desenvolvedor Java com Spring Boot",
                "Experiencia com PostgreSQL e Docker");

        TextoExtraido extraido = extrator.extrair(pdf);

        assertThat(extraido.origem()).isEqualTo(OrigemTexto.CAMADA_DE_TEXTO);
        assertThat(extraido.paginas()).isEqualTo(1);
        assertThat(extraido.texto())
                .contains("Carlos Pereira")
                .contains("Spring Boot")
                .contains("PostgreSQL");
    }

    @Test
    @DisplayName("PDF em que a pagina e imagem e lido por OCR")
    void somenteImagemCaiParaOcr() {
        byte[] pdf = FabricaDePdf.somenteImagem(
                "Joao Lima",
                "Desenvolvedor Java com Spring Boot",
                "Experiencia com PostgreSQL");

        TextoExtraido extraido = extrator.extrair(pdf);

        assertThat(extraido.origem()).isEqualTo(OrigemTexto.OCR);
        assertThat(extraido.paginas()).isEqualTo(1);
        assertThat(semAcentoEmMinusculas(extraido.texto()))
                .contains("joao lima")
                .contains("java")
                .contains("postgresql");
    }

    @Test
    @DisplayName("OCR percorre todas as paginas de um PDF de imagens")
    void variasPaginasDeImagem() {
        byte[] pdf = FabricaDePdf.somenteImagemComVariasPaginas(
                new String[] {"Carlos Pereira", "Desenvolvedor Java"},
                new String[] {"Certificado em PostgreSQL", "Kubernetes em producao"});

        TextoExtraido extraido = extrator.extrair(pdf);

        assertThat(extraido.origem()).isEqualTo(OrigemTexto.OCR);
        assertThat(extraido.paginas()).isEqualTo(2);
        assertThat(semAcentoEmMinusculas(extraido.texto()))
                .contains("java")
                .contains("postgresql")
                .contains("kubernetes");
    }

    @Test
    @DisplayName("camada de texto curtissima nao conta: cai para OCR")
    void camadaDeTextoAbaixoDoMinimoCaiParaOcr() {
        byte[] pdf = FabricaDePdf.imagemComCamadaDeTextoCurta(
                "p. 1",
                "Joao Lima",
                "Especialista em Kotlin e Postgres");

        TextoExtraido extraido = extrator.extrair(pdf);

        assertThat(extraido.origem()).isEqualTo(OrigemTexto.OCR);
        assertThat(semAcentoEmMinusculas(extraido.texto()))
                .contains("joao lima")
                .contains("kotlin");
    }

    @Test
    @DisplayName("pagina em branco nao rende texto nem na camada nem no OCR")
    void paginaEmBrancoEhRecusada() {
        byte[] pdf = FabricaDePdf.paginaEmBranco();

        assertThatThrownBy(() -> extrator.extrair(pdf))
                .isInstanceOf(CurriculoInvalidoException.class)
                .hasMessageContaining("texto");
    }

    @Test
    @DisplayName("arquivo que nao e PDF e recusado")
    void arquivoQueNaoEhPdf() {
        byte[] lixo = "nao sou um pdf, sou um texto solto".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> extrator.extrair(lixo))
                .isInstanceOf(CurriculoInvalidoException.class);
    }

    @Test
    @DisplayName("arquivo vazio e recusado")
    void arquivoVazio() {
        assertThatThrownBy(() -> extrator.extrair(new byte[0]))
                .isInstanceOf(CurriculoInvalidoException.class);
    }

    @Test
    @DisplayName("o texto extraido preserva paragrafos e colapsa espacos")
    void normalizacaoDeEspacos() {
        byte[] pdf = FabricaDePdf.comCamadaDeTexto(
                "Carlos     Pereira",
                "Desenvolvedor Java com Spring Boot e PostgreSQL");

        String texto = extrator.extrair(pdf).texto();

        assertThat(texto).contains("Carlos Pereira");
        assertThat(texto).doesNotContain("  ");
        assertThat(texto).doesNotStartWith("\n").doesNotEndWith("\n");
        assertThat(texto).contains("\n");
    }

    /** O OCR acerta as palavras, nao necessariamente os acentos; compare o que importa. */
    private static String semAcentoEmMinusculas(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }
}
