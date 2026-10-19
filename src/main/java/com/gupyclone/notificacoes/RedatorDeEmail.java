package com.gupyclone.notificacoes;

/**
 * Escreve assunto e corpo do aviso ao candidato.
 *
 * <p>Separado da fila para que o texto possa mudar (ou virar template, ou sair
 * de uma LLM) sem mexer em entrega nem em transacao.
 */
public interface RedatorDeEmail {

    EmailOutbox redigir(AvisoAoCandidato aviso);
}
