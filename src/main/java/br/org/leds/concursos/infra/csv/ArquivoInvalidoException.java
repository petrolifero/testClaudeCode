package br.org.leds.concursos.infra.csv;

/**
 * Erro de leitura dos arquivos de entrada, com a linha em que ocorreu.
 */
public class ArquivoInvalidoException extends RuntimeException {

    public ArquivoInvalidoException(String arquivo, long linha, String motivo, Throwable causa) {
        super("%s, linha %d: %s".formatted(arquivo, linha, motivo), causa);
    }

    public ArquivoInvalidoException(String arquivo, String motivo, Throwable causa) {
        super("%s: %s".formatted(arquivo, motivo), causa);
    }
}
