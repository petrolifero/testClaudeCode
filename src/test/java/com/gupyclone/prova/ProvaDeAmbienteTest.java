package com.gupyclone.prova;

import static org.assertj.core.api.Assertions.assertThat;

import com.gupyclone.suporte.FabricaDePdf;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Prova de ambiente: garante que a maquina que roda a suite tem o que o
 * projeto precisa (PDFBox lendo camada de texto e o binario do Tesseract
 * fazendo OCR). Falha cedo e com mensagem clara, em vez de deixar os testes de
 * curriculo falharem por um motivo que nao e deles.
 */
class ProvaDeAmbienteTest {

    @Test
    @DisplayName("PDF com camada de texto e lido pelo PDFBox")
    void pdfComCamadaDeTexto() throws Exception {
        byte[] pdf = FabricaDePdf.comCamadaDeTexto("Carlos Pereira", "Desenvolvedor Java com Spring Boot");

        try (PDDocument documento = Loader.loadPDF(pdf)) {
            String texto = new PDFTextStripper().getText(documento);
            assertThat(texto).contains("Carlos Pereira").contains("Spring Boot");
        }
    }

    @Test
    @DisplayName("PDF somente imagem nao tem camada de texto")
    void pdfSomenteImagemNaoTemTexto() throws Exception {
        byte[] pdf = FabricaDePdf.somenteImagem("Joao Lima", "Desenvolvedor Java");

        try (PDDocument documento = Loader.loadPDF(pdf)) {
            String texto = new PDFTextStripper().getText(documento);
            assertThat(texto.replaceAll("\\s", "")).isEmpty();
        }
    }

    @Test
    @DisplayName("o binario do Tesseract esta instalado e le uma pagina rasterizada")
    void tesseractEstaDisponivel() throws Exception {
        byte[] pdf = FabricaDePdf.somenteImagem(
                "Joao Lima", "Desenvolvedor Java com Spring Boot", "Experiencia com PostgreSQL");

        Path png = Files.createTempFile("prova-ocr", ".png");
        try (PDDocument documento = Loader.loadPDF(pdf)) {
            BufferedImage imagem = new PDFRenderer(documento).renderImageWithDPI(0, 300);
            ImageIO.write(imagem, "png", png.toFile());
        }

        Process processo = new ProcessBuilder("tesseract", png.toString(), "stdout", "-l", "por+eng")
                .redirectErrorStream(false)
                .redirectError(new File("/dev/null"))
                .start();
        String reconhecido = new String(processo.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(processo.waitFor(60, TimeUnit.SECONDS)).as("tesseract deve terminar").isTrue();
        assertThat(processo.exitValue())
                .as("instale o tesseract: apt-get install -y tesseract-ocr tesseract-ocr-por")
                .isZero();
        Files.deleteIfExists(png);

        String normalizado = reconhecido.toLowerCase(Locale.ROOT);
        assertThat(normalizado).contains("joao").contains("java").contains("postgresql");
    }
}
