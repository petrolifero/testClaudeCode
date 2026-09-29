package br.leds.comum.erro;

/** Falha em recurso externo (banco fora do ar, SQL rejeitado, recurso ausente): não é culpa da entrada. */
public class ErroInfraestrutura extends ErroAplicacao {

    public ErroInfraestrutura(String mensagem) {
        super(mensagem);
    }

    public ErroInfraestrutura(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
