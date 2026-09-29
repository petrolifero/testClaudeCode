package br.leds.comum;

/** Erro esperado (entrada inválida, arquivo ausente, falha de banco) com mensagem apresentável ao usuário. */
public class ErroAplicacao extends RuntimeException {

    public ErroAplicacao(String mensagem) {
        super(mensagem);
    }

    public ErroAplicacao(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
