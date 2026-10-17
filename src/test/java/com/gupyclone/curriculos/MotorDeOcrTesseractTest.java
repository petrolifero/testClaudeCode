package com.gupyclone.curriculos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gupyclone.config.PropriedadesOcr;
import com.gupyclone.suporte.FabricaDePdf;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.Locale;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Prova do motor de OCR em si, com o binario do Tesseract de verdade.
 *
 * <p>O caminho de falha nao usa dublê: aponta o Tesseract real para um
 * diretorio de dados vazio, que e exatamente o que acontece numa maquina em
 * que o pacote de idioma nao foi instalado.
 */
class MotorDeOcrTesseractTest {

    private static final String DATAPATH =
            System.getenv().getOrDefault("GUPY_OCR_DATAPATH", "/usr/share/tesseract-ocr/5/tessdata");

    private static final String EXECUTAVEL =
            System.getenv().getOrDefault("GUPY_OCR_EXECUTAVEL", "tesseract");

    @Test
    @DisplayName("reconhece o texto de uma pagina rasterizada")
    void reconheceTextoDaPagina() throws IOException {
        MotorDeOcr motor = new MotorDeOcrTesseract(new PropriedadesOcr("por+eng", DATAPATH, 300, 40, EXECUTAVEL));

        String reconhecido = motor.reconhecer(rasterizar(FabricaDePdf.somenteImagem(
                "Carlos Pereira", "Desenvolvedor Java com Spring Boot")));

        assertThat(semAcentoEmMinusculas(reconhecido)).contains("carlos pereira").contains("java");
    }

    @Test
    @DisplayName("sem os dados de idioma o erro diz como instalar, em vez de devolver texto vazio")
    void semDadosDeIdiomaFalhaComErroClaro() throws IOException {
        Path semIdiomas = Files.createTempDirectory("tessdata-vazio");
        try {
            MotorDeOcr motor = new MotorDeOcrTesseract(
                    new PropriedadesOcr("por+eng", semIdiomas.toString(), 300, 40, EXECUTAVEL));
            BufferedImage pagina = rasterizar(FabricaDePdf.somenteImagem("Carlos Pereira"));

            assertThatThrownBy(() -> motor.reconhecer(pagina))
                    .isInstanceOf(OcrIndisponivelException.class)
                    .hasMessageContaining("tesseract-ocr-por");
        } finally {
            Files.deleteIfExists(semIdiomas);
        }
    }

    @Test
    @DisplayName("nao vaza arquivo temporario de OCR")
    void naoDeixaTemporarioParaTras() throws IOException {
        MotorDeOcr motor = new MotorDeOcrTesseract(new PropriedadesOcr("por+eng", DATAPATH, 300, 40, EXECUTAVEL));
        BufferedImage pagina = rasterizar(FabricaDePdf.somenteImagem("Carlos Pereira"));
        long antes = temporariosDeOcr();

        motor.reconhecer(pagina);

        assertThat(temporariosDeOcr()).isEqualTo(antes);
    }

    @Test
    @DisplayName("binario de OCR inexistente da erro com a receita de instalacao")
    void binarioAusente() throws IOException {
        // So da para provar este caminho porque gupy.ocr.executavel e
        // configuravel: aqui aponta para um binario que nao existe.
        MotorDeOcr motor = new MotorDeOcrTesseract(
                new PropriedadesOcr("por+eng", DATAPATH, 300, 40, "tesseract-que-nao-existe"));
        BufferedImage pagina = rasterizar(FabricaDePdf.somenteImagem("Carlos Pereira"));

        assertThatExceptionOfType(OcrIndisponivelException.class)
                .isThrownBy(() -> motor.reconhecer(pagina))
                .withMessageContaining("apt-get install");
    }

    private static long temporariosDeOcr() throws IOException {
        Path temporarios = Path.of(System.getProperty("java.io.tmpdir"));
        try (var arquivos = Files.list(temporarios)) {
            return arquivos.filter(arquivo -> arquivo.getFileName().toString().startsWith("gupy-ocr-")).count();
        }
    }

    private static BufferedImage rasterizar(byte[] pdf) throws IOException {
        try (PDDocument documento = Loader.loadPDF(pdf)) {
            return new PDFRenderer(documento).renderImageWithDPI(0, 300);
        }
    }

    private static String semAcentoEmMinusculas(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }
}
