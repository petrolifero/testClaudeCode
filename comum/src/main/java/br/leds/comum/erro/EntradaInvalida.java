package br.leds.comum.erro;

/** O dado informado pelo usuário/arquivo está errado (CPF malformado, código não numérico, CSV inválido...). */
public class EntradaInvalida extends ErroAplicacao {

    public EntradaInvalida(String mensagem) {
        super(mensagem);
    }

    public EntradaInvalida(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
