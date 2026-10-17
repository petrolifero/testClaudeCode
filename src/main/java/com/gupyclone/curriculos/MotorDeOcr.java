package com.gupyclone.curriculos;

import java.awt.image.BufferedImage;

/**
 * Reconhecimento optico de caracteres sobre uma pagina rasterizada.
 *
 * <p>Seam de troca: hoje Tesseract local; amanha um servico de OCR em nuvem.
 */
public interface MotorDeOcr {

    String reconhecer(BufferedImage imagem);
}
