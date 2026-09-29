package br.org.leds.concursos.dominio;

/**
 * Lançada quando um valor recebido (da API ou dos arquivos) não respeita as regras do domínio.
 */
public class DadoInvalidoException extends RuntimeException {

    public DadoInvalidoException(String mensagem) {
        super(mensagem);
    }
}
