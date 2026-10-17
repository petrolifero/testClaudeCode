package com.gupyclone.web;

import java.util.Map;

/**
 * Traduz o parametro {@code ?aviso=} em texto para a pessoa.
 *
 * <p>A pagina inicial recebe o aviso por querystring porque e para la que a
 * cadeia de seguranca redireciona quem tenta abrir uma pagina protegida sem
 * estar autenticado. Chave desconhecida nao vira texto: assim ninguem injeta
 * uma mensagem arbitraria pela URL.
 */
final class Avisos {

    private static final Map<String, String> TEXTOS = Map.of(
            "entre-para-continuar", "Entre como uma das pessoas ao lado para continuar.",
            "saia-antes-de-entrar-como-outra-pessoa", "Você já está em uma conta. Saia antes de entrar como outra pessoa.");

    private Avisos() {
    }

    static String texto(String chave) {
        return chave == null ? null : TEXTOS.get(chave);
    }
}
