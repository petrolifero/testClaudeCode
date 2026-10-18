package com.gupyclone.curriculos;

import com.gupyclone.config.PropriedadesOcr;
import java.io.IOException;
import java.util.regex.Pattern;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Le o texto do PDF pela camada de texto e, quando ela nao existe ou e
 * insignificante, rasteriza cada pagina e manda para o OCR.
 *
 * <p>A decisao entre os dois caminhos e um limiar de caracteres, nao um
 * "tem ou nao tem texto": digitalizadores costumam embutir uma camada
 * minuscula (numero de pagina, marca d'agua, nome do scanner) que passaria por
 * conteudo e faria o curriculo chegar vazio na triagem.
 */
@Component
public class ExtratorDeTextoComOcrDeFallback implements ExtratorDeTextoDePdf {

    private static final Logger log = LoggerFactory.getLogger(ExtratorDeTextoComOcrDeFallback.class);

    private static final Pattern ESPACOS_NA_MESMA_LINHA = Pattern.compile("[ \\t\\x0B\\f\\u00a0]+");
    private static final Pattern ESPACO_NA_BORDA_DA_LINHA = Pattern.compile("(?m)^ +| +$");
    private static final Pattern LINHAS_VAZIAS_DEMAIS = Pattern.compile("\\n{3,}");
    private static final Pattern CONTROLES_INVISIVEIS =
            Pattern.compile("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]");

    /**
     * Teto do texto guardado. O limite real e do indice: a coluna gerada
     * texto_busca e um tsvector, e o PostgreSQL recusa tsvector acima de 1 MB
     * ("string is too long for tsvector"). Sem este corte, um PDF legitimo de
     * poucas centenas de KB derrubava a submissao com erro 500 de banco em vez
     * de ser aceito.
     */
    static final int MAXIMO_DE_CARACTERES = 400_000;

    private final MotorDeOcr ocr;
    private final PropriedadesOcr propriedades;

    public ExtratorDeTextoComOcrDeFallback(MotorDeOcr ocr, PropriedadesOcr propriedades) {
        if (propriedades.dpi() <= 0) {
            throw new IllegalArgumentException("gupy.ocr.dpi tem que ser positivo, veio " + propriedades.dpi());
        }
        this.ocr = ocr;
        this.propriedades = propriedades;
    }

    @Override
    public TextoExtraido extrair(byte[] pdf) {
        if (pdf == null || pdf.length == 0) {
            throw new CurriculoInvalidoException("o arquivo enviado esta vazio");
        }
        try (PDDocument documento = Loader.loadPDF(pdf)) {
            int paginas = documento.getNumberOfPages();
            if (paginas == 0) {
                throw new CurriculoInvalidoException("o PDF enviado nao tem nenhuma pagina");
            }

            String camadaDeTexto = normalizar(new PDFTextStripper().getText(documento));
            if (camadaDeTexto.length() >= Math.max(1, propriedades.minimoDeCaracteresNaCamadaDeTexto())) {
                return new TextoExtraido(camadaDeTexto, OrigemTexto.CAMADA_DE_TEXTO, paginas);
            }
            // So aqui os tetos importam: um PDF com camada de texto nao e
            // rasterizado, por isso nao custa paginas nem pixels.
            recusarSeForBombaDeOcr(documento, paginas);

            log.debug("camada de texto com {} caracteres; caindo para OCR em {} pagina(s)",
                    camadaDeTexto.length(), paginas);
            String reconhecido = normalizar(reconhecerPaginas(documento, paginas));
            if (reconhecido.isEmpty()) {
                throw new CurriculoInvalidoException(
                        "nao foi possivel ler texto algum do PDF, nem na camada de texto nem por OCR");
            }
            return new TextoExtraido(reconhecido, OrigemTexto.OCR, paginas);
        } catch (InvalidPasswordException e) {
            throw new CurriculoInvalidoException("o PDF enviado esta protegido por senha", e);
        } catch (IOException e) {
            throw new CurriculoInvalidoException("o arquivo enviado nao e um PDF legivel", e);
        }
    }

    /**
     * Recusa PDF que custaria caro demais para rasterizar.
     *
     * <p>Dois ataques baratos existiam: um PDF de 211 KB com 20.000 paginas em
     * branco, que prendia o worker por mais de 15 minutos so rasterizando; e
     * uma unica pagina com MediaBox enorme, que estourava o heap com
     * OutOfMemoryError — e OutOfMemoryError e Error, nao Exception, portanto
     * escapava do catch e nunca virava 422. A guarda do proprio PDFBox so pega
     * o estouro de int (w*h*escala^2 acima de 2^31), deixando uma faixa de
     * tamanhos que derruba a JVM antes disso.
     */
    private void recusarSeForBombaDeOcr(PDDocument documento, int paginas) {
        if (paginas > propriedades.maxPaginas()) {
            throw new CurriculoInvalidoException("o PDF enviado tem " + paginas
                    + " paginas sem texto selecionavel; o maximo para leitura por OCR e "
                    + propriedades.maxPaginas());
        }
        double escala = propriedades.dpi() / 72.0;
        for (int indice = 0; indice < paginas; indice++) {
            var caixa = documento.getPage(indice).getCropBox();
            long pixels = Math.round(caixa.getWidth() * escala) * Math.round(caixa.getHeight() * escala);
            if (pixels > propriedades.maxPixelsPorPagina()) {
                throw new CurriculoInvalidoException("a pagina " + (indice + 1)
                        + " do PDF enviado e grande demais para ser lida por OCR");
            }
        }
    }

    private String reconhecerPaginas(PDDocument documento, int paginas) throws IOException {
        PDFRenderer renderizador = new PDFRenderer(documento);
        StringBuilder texto = new StringBuilder();
        for (int pagina = 0; pagina < paginas; pagina++) {
            String daPagina = ocr.reconhecer(renderizador.renderImageWithDPI(pagina, propriedades.dpi())).strip();
            if (daPagina.isEmpty()) {
                continue;
            }
            if (!texto.isEmpty()) {
                // Cada pagina e um paragrafo: a linha vazia preserva a fronteira
                // sem inventar separador que a busca textual teria de ignorar.
                texto.append("\n\n");
            }
            texto.append(daPagina);
        }
        return texto.toString();
    }

    /**
     * Colapsa espacos e linhas vazias em excesso, mantendo quebra de linha e
     * quebra de paragrafo: a camada de texto do PDF e o OCR produzem
     * espacamento erratico, e o texto ainda vai ser lido por gente.
     */
    static String normalizar(String bruto) {
        if (bruto == null || bruto.isEmpty()) {
            return "";
        }
        String texto = bruto.replace("\r\n", "\n").replace('\r', '\n');
        texto = ESPACOS_NA_MESMA_LINHA.matcher(texto).replaceAll(" ");
        texto = ESPACO_NA_BORDA_DA_LINHA.matcher(texto).replaceAll("");
        // Byte NUL e companhia chegam de PDF malformado e de OCR, e o
        // PostgreSQL recusa NUL em texto ("invalid byte sequence for encoding
        // UTF8: 0x00"), derrubando a submissao com erro de banco.
        texto = CONTROLES_INVISIVEIS.matcher(texto).replaceAll("");
        texto = ESPACOS_NA_MESMA_LINHA.matcher(texto).replaceAll(" ");
        texto = LINHAS_VAZIAS_DEMAIS.matcher(texto).replaceAll("\n\n");
        texto = texto.strip();
        return texto.length() > MAXIMO_DE_CARACTERES ? texto.substring(0, MAXIMO_DE_CARACTERES).strip() : texto;
    }
}
