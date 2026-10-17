package com.gupyclone.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuracao do OCR.
 *
 * @param idiomas                            idiomas do Tesseract, ex: "por+eng"
 * @param datapath                           diretorio com os arquivos .traineddata
 * @param dpi                                resolucao usada ao rasterizar paginas do PDF
 * @param minimoDeCaracteresNaCamadaDeTexto  abaixo disso o PDF e tratado como imagem e vai para OCR
 */
@ConfigurationProperties(prefix = "gupy.ocr")
public record PropriedadesOcr(
        String idiomas,
        String datapath,
        int dpi,
        int minimoDeCaracteresNaCamadaDeTexto) {
}
