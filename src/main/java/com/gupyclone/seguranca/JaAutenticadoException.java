package com.gupyclone.seguranca;

/**
 * Tentativa de entrar como outra persona sem sair primeiro.
 *
 * <p>Exigencia do enunciado: "a possibilidade de logar em outro some uma vez
 * que esta logado, precisando deslogar antes pra logar em outro".
 */
public class JaAutenticadoException extends RuntimeException {

    public JaAutenticadoException(String chaveAtual) {
        super("ja autenticado como '" + chaveAtual + "'; saia antes de entrar como outra pessoa");
    }
}
