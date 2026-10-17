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
 */
@ConfigurationProperties(prefix = "gupy.ocr")
public record PropriedadesOcr(
        String idiomas,
        String datapath,
        int dpi,
        int minimoDeCaracteresNaCamadaDeTexto,
        String executavel) {

    public PropriedadesOcr {
        if (executavel == null || executavel.isBlank()) {
            executavel = "tesseract";
        }
    }
}
