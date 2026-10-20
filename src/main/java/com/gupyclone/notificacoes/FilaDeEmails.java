package com.gupyclone.notificacoes;

/**
 * Porta usada pela triagem para avisar o candidato, sem conhecer SMTP nem o
 * texto das mensagens.
 *
 * <p>Enfileirar e sempre feito <em>dentro</em> da transacao que define o status
 * final da candidatura (padrao outbox): ou os dois acontecem, ou nenhum.
 */
public interface FilaDeEmails {

    /**
     * Coloca um aviso na fila. Chamar duas vezes para a mesma
     * {@code (candidatura, tipo)} nao gera dois e-mails: o banco tem unique
     * nessa combinacao.
     */
    void enfileirar(AvisoAoCandidato aviso);
}
