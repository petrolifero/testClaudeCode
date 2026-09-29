package br.leds.comum.erro;

/**
 * Erro esperado da aplicação, com mensagem apresentável ao usuário. Especializações dizem <i>de quem</i> é a
 * culpa ({@link EntradaInvalida} ou {@link ErroInfraestrutura}), para que cada porta de entrada (CLI, REST)
 * decida como traduzir: código de saída, status HTTP etc.
 */
public class ErroAplicacao extends RuntimeException {

    public ErroAplicacao(String mensagem) {
        super(mensagem);
    }

    public ErroAplicacao(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
