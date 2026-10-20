package com.gupyclone.curriculos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.gupyclone.config.PropriedadesOcr;
import com.gupyclone.suporte.FabricaDePdf;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * PDF valido mas abusivo: barato de montar, caro de processar.
 *
 * <p>Cada caso aqui era uma forma de derrubar ou prender o servidor com um
 * upload legitimo, encontrada pela revisao adversarial e reproduzida antes de
 * ser consertada.
 */
class PdfAbusivoTest {

    private static final PropriedadesOcr PROPRIEDADES = new PropriedadesOcr(
            System.getenv().getOrDefault("GUPY_OCR_IDIOMAS", "por+eng"),
            System.getenv().getOrDefault("GUPY_OCR_DATAPATH", "/usr/share/tesseract-ocr/5/tessdata"),
            300,
            40,
            System.getenv().getOrDefault("GUPY_OCR_EXECUTAVEL", "tesseract"),
            30,
            50_000_000L);

    private final ExtratorDeTextoDePdf extrator =
            new ExtratorDeTextoComOcrDeFallback(new MotorDeOcrTesseract(PROPRIEDADES), PROPRIEDADES);

    @Test
    @DisplayName("PDF com paginas demais e recusado sem rasterizar nenhuma")
    void muitasPaginasSaoRecusadas() {
        // 2.000 paginas em branco cabem em poucos KB; a 300 DPI, rasterizar
        // tudo levaria minutos e prenderia o worker.
        byte[] pdf = FabricaDePdf.muitasPaginasSemTexto(2_000);
        assertThat(pdf.length).as("barato de montar").isLessThan(1_000_000);

        long inicio = System.nanoTime();
        assertThatExceptionOfType(CurriculoInvalidoException.class)
                .isThrownBy(() -> extrator.extrair(pdf))
                .withMessageContaining("paginas");
        long milissegundos = (System.nanoTime() - inicio) / 1_000_000;

        assertThat(milissegundos)
                .as("a recusa e imediata: nenhuma pagina foi rasterizada")
                .isLessThan(5_000);
    }

    @Test
    @DisplayName("pagina com dimensoes enormes e recusada em vez de estourar o heap")
    void paginaGigantescaERecusada() {
        // 5000x5000 pontos a 300 DPI dao mais de 400 milhoes de pixels. A
        // guarda do PDFBox so pega o estouro de int, muito acima disso, entao
        // sem teto proprio isto virava OutOfMemoryError -- que, sendo Error,
        // escapava do tratamento e nunca virava recusa.
        byte[] pdf = FabricaDePdf.paginaGigantesca(5_000);

        assertThatExceptionOfType(CurriculoInvalidoException.class)
                .isThrownBy(() -> extrator.extrair(pdf))
                .withMessageContaining("grande demais");
    }

    @Test
    @DisplayName("pagina de tamanho normal continua indo para OCR: o teto nao recusa demais")
    void paginaDentroDoOrcamentoPassa() {
        // A4 (595x842 pt) a 300 DPI da ~8,7 milhoes de pixels, bem abaixo do
        // teto de 50 milhoes. O teto precisa barrar a bomba sem barrar o
        // curriculo digitalizado legitimo, que e o caso de uso do enunciado.
        byte[] pdf = FabricaDePdf.somenteImagem("Joao Lima", "Desenvolvedor Java com Spring Boot");

        var extraido = extrator.extrair(pdf);

        assertThat(extraido.origem()).isEqualTo(OrigemTexto.OCR);
        assertThat(extraido.texto().toLowerCase(java.util.Locale.ROOT)).contains("java");
    }

    @Test
    @DisplayName("texto imenso e truncado abaixo do limite do indice de busca")
    void textoImensoETruncado() {
        byte[] pdf = FabricaDePdf.camadaDeTextoEnorme(200, 160);

        var extraido = extrator.extrair(pdf);

        assertThat(extraido.origem()).isEqualTo(OrigemTexto.CAMADA_DE_TEXTO);
        assertThat(extraido.texto().length())
                .as("o PostgreSQL recusa tsvector acima de 1 MB; o texto tem de caber")
                .isLessThanOrEqualTo(ExtratorDeTextoComOcrDeFallback.MAXIMO_DE_CARACTERES);
        assertThat(extraido.texto()).contains("Java");
    }

    @Test
    @DisplayName("controles invisiveis, inclusive NUL, saem do texto")
    void controlesInvisiveisSaem() {
        String comLixo = "Carlos\u0000 Pereira\u0001\nDesenvolvedor\u0008 Java\u001f";

        String limpo = ExtratorDeTextoComOcrDeFallback.normalizar(comLixo);

        assertThat(limpo)
                .as("o PostgreSQL recusa NUL em texto e derrubava a submissao com erro de banco")
                .doesNotContain("\u0000").doesNotContain("\u0001")
                .doesNotContain("\u0008").doesNotContain("\u001f");
        assertThat(limpo).contains("Carlos").contains("Pereira").contains("Java");
        assertThat(limpo).contains("\n");
    }
}
