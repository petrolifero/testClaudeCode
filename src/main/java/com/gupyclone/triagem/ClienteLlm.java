package com.gupyclone.triagem;

/**
 * Porta para um modelo de linguagem, com um unico metodo.
 *
 * <p>Existe para que {@link AnalisadorViaLlm} fale de prompt e de veredito, e
 * nao de HTTP, cabecalhos e JSON de transporte. Trocar de provedor (ou falar
 * com um modelo local) e escrever outra implementacao desta interface.
 */
public interface ClienteLlm {

    /**
     * @param instrucao instrucao de sistema: o papel e o formato da resposta
     * @param pergunta  a mensagem do usuario
     * @return o texto que o modelo respondeu
     * @throws AnaliseIndisponivelException se o modelo nao responder ou
     *                                      responder erro
     */
    String perguntar(String instrucao, String pergunta);
}
