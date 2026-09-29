package br.org.leds.concursos.dominio;

/**
 * Lançada quando o CPF ou o código de concurso buscado não existe na base.
 */
public class RecursoNaoEncontradoException extends RuntimeException {

    public RecursoNaoEncontradoException(String mensagem) {
        super(mensagem);
    }
}
