package com.gupyclone.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuracao do OCR.
 *
 * @param idiomas                            idiomas do Tesseract, ex: "por+eng"
 * @param datapath                           diretorio com os arquivos .traineddata
 * @param dpi                                resolucao usada ao rasterizar paginas do PDF
 * @param minimoDeCaracteresNaCamadaDeTexto  abaixo disso o PDF e tratado como imagem e vai para OCR
 * @param executavel                         binario do Tesseract; so mude para apontar para outro caminho
 * @param maxPaginas                         acima disso o PDF e recusado em vez de ocupar o worker por minutos
 * @param maxPixelsPorPagina                 orcamento de rasterizacao por pagina, para uma pagina gigante
 *                                           nao estourar o heap antes de qualquer guarda do PDFBox
 */
@ConfigurationProperties(prefix = "gupy.ocr")
public record PropriedadesOcr(
        String idiomas,
        String datapath,
        int dpi,
        int minimoDeCaracteresNaCamadaDeTexto,
        String executavel,
        int maxPaginas,
        long maxPixelsPorPagina) {

    public PropriedadesOcr {
        if (executavel == null || executavel.isBlank()) {
            executavel = "tesseract";
        }
        if (maxPaginas <= 0) {
            maxPaginas = 30;
        }
        if (maxPixelsPorPagina <= 0) {
            maxPixelsPorPagina = 50_000_000L;
        }
    }
}
