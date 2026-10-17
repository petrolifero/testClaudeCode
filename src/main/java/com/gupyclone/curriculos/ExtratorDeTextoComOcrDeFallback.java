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
        texto = LINHAS_VAZIAS_DEMAIS.matcher(texto).replaceAll("\n\n");
        return texto.strip();
    }
}
