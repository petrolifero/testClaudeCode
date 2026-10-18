package com.gupyclone.suporte;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

/**
 * Gera curriculos em PDF de verdade para os testes.
 *
 * <p>Sao fixtures, nao mocks: os bytes produzidos aqui sao PDFs validos, lidos
 * pelo mesmo PDFBox que a aplicacao usa. Existem duas variantes porque o
 * enunciado pede as duas:
 * <ul>
 *   <li>{@link #comCamadaDeTexto} — PDF "bonzinho", com texto selecionavel;</li>
 *   <li>{@link #somenteImagem} — PDF em que cada pagina e uma imagem, sem
 *       nenhum texto embutido, que so pode ser lido por OCR.</li>
 * </ul>
 */
public final class FabricaDePdf {

    /** A4 a 200 DPI: resolucao confortavel para o Tesseract. */
    private static final int LARGURA_EM_PIXELS = 1654;
    private static final int ALTURA_EM_PIXELS = 2339;

    private FabricaDePdf() {
    }

    /** PDF com camada de texto selecionavel. */
    public static byte[] comCamadaDeTexto(String... linhas) {
        try (PDDocument documento = new PDDocument()) {
            PDPage pagina = new PDPage(PDRectangle.A4);
            documento.addPage(pagina);
            try (PDPageContentStream conteudo = new PDPageContentStream(documento, pagina)) {
                conteudo.beginText();
                conteudo.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                conteudo.setLeading(16);
                conteudo.newLineAtOffset(50, 780);
                for (String linha : linhas) {
                    conteudo.showText(linha);
                    conteudo.newLine();
                }
                conteudo.endText();
            }
            return emBytes(documento);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * PDF cuja unica pagina e uma imagem com o texto desenhado: nao tem camada
     * de texto, portanto exige OCR.
     */
    public static byte[] somenteImagem(String... linhas) {
        try (PDDocument documento = new PDDocument()) {
            PDPage pagina = new PDPage(PDRectangle.A4);
            documento.addPage(pagina);
            PDImageXObject imagem = LosslessFactory.createFromImage(documento, desenhar(linhas));
            try (PDPageContentStream conteudo = new PDPageContentStream(documento, pagina)) {
                PDRectangle caixa = pagina.getMediaBox();
                conteudo.drawImage(imagem, 0, 0, caixa.getWidth(), caixa.getHeight());
            }
            return emBytes(documento);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** PDF valido, porem sem texto algum: nem camada de texto, nem imagem legivel. */
    public static byte[] paginaEmBranco() {
        try (PDDocument documento = new PDDocument()) {
            documento.addPage(new PDPage(PDRectangle.A4));
            return emBytes(documento);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * PDF com varias paginas, cada uma uma imagem: so esta lido por completo
     * quem rasterizar e passar por OCR <em>todas</em> as paginas.
     *
     * @param paginas as linhas de cada pagina, na ordem
     */
    public static byte[] somenteImagemComVariasPaginas(String[]... paginas) {
        try (PDDocument documento = new PDDocument()) {
            for (String[] linhas : paginas) {
                PDPage pagina = new PDPage(PDRectangle.A4);
                documento.addPage(pagina);
                PDImageXObject imagem = LosslessFactory.createFromImage(documento, desenhar(linhas));
                try (PDPageContentStream conteudo = new PDPageContentStream(documento, pagina)) {
                    PDRectangle caixa = pagina.getMediaBox();
                    conteudo.drawImage(imagem, 0, 0, caixa.getWidth(), caixa.getHeight());
                }
            }
            return emBytes(documento);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * PDF de imagem com uma camada de texto minuscula por cima — um numero de
     * pagina, uma marca d'agua do digitalizador. E o caso traicoeiro: existe
     * camada de texto, mas ela nao e o curriculo, e so o OCR le o conteudo.
     *
     * @param textoCurto o pouco texto selecionavel da pagina
     * @param linhas     o conteudo de verdade, desenhado na imagem
     */
    public static byte[] imagemComCamadaDeTextoCurta(String textoCurto, String... linhas) {
        try (PDDocument documento = new PDDocument()) {
            PDPage pagina = new PDPage(PDRectangle.A4);
            documento.addPage(pagina);
            PDImageXObject imagem = LosslessFactory.createFromImage(documento, desenhar(linhas));
            try (PDPageContentStream conteudo = new PDPageContentStream(documento, pagina)) {
                PDRectangle caixa = pagina.getMediaBox();
                conteudo.drawImage(imagem, 0, 0, caixa.getWidth(), caixa.getHeight());
                conteudo.beginText();
                conteudo.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 8);
                conteudo.newLineAtOffset(40, 24);
                conteudo.showText(textoCurto);
                conteudo.endText();
            }
            return emBytes(documento);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * PDF valido, leve em bytes e caro em processamento: muitas paginas sem
     * camada de texto. Serve para provar que existe teto de paginas antes de
     * qualquer rasterizacao.
     */
    public static byte[] muitasPaginasSemTexto(int quantidade) {
        try (PDDocument documento = new PDDocument()) {
            for (int pagina = 0; pagina < quantidade; pagina++) {
                documento.addPage(new PDPage(PDRectangle.A4));
            }
            return emBytes(documento);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Uma pagina unica com dimensoes enormes declaradas no MediaBox. Rasterizar
     * isso estourava o heap com OutOfMemoryError, que por ser Error escapava do
     * tratamento de erro e nunca virava recusa.
     */
    public static byte[] paginaGigantesca(float lado) {
        try (PDDocument documento = new PDDocument()) {
            documento.addPage(new PDPage(new PDRectangle(lado, lado)));
            return emBytes(documento);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * PDF com camada de texto longa de verdade, para exercitar o teto de
     * tamanho do texto guardado (o tsvector do PostgreSQL recusa acima de 1 MB).
     */
    public static byte[] camadaDeTextoEnorme(int paginas, int linhasPorPagina) {
        try (PDDocument documento = new PDDocument()) {
            var fonte = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            for (int pagina = 0; pagina < paginas; pagina++) {
                PDPage folha = new PDPage(PDRectangle.A4);
                documento.addPage(folha);
                try (PDPageContentStream conteudo = new PDPageContentStream(documento, folha)) {
                    conteudo.beginText();
                    conteudo.setFont(fonte, 4);
                    conteudo.setLeading(5);
                    conteudo.newLineAtOffset(20, 820);
                    for (int linha = 0; linha < linhasPorPagina; linha++) {
                        conteudo.showText("Java Spring PostgreSQL experiencia desenvolvimento plataforma "
                                + "integracao mensageria observabilidade " + pagina + "-" + linha);
                        conteudo.newLine();
                    }
                    conteudo.endText();
                }
            }
            return emBytes(documento);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static BufferedImage desenhar(String... linhas) {
        BufferedImage imagem = new BufferedImage(LARGURA_EM_PIXELS, ALTURA_EM_PIXELS, BufferedImage.TYPE_INT_RGB);
        Graphics2D grafico = imagem.createGraphics();
        try {
            grafico.setColor(Color.WHITE);
            grafico.fillRect(0, 0, LARGURA_EM_PIXELS, ALTURA_EM_PIXELS);
            grafico.setColor(Color.BLACK);
            grafico.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            grafico.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            grafico.setFont(new Font("DejaVu Sans", Font.PLAIN, 44));
            int y = 160;
            for (String linha : linhas) {
                grafico.drawString(linha, 120, y);
                y += 78;
            }
        } finally {
            grafico.dispose();
        }
        return imagem;
    }

    private static byte[] emBytes(PDDocument documento) throws IOException {
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        documento.save(saida);
        return saida.toByteArray();
    }
}
