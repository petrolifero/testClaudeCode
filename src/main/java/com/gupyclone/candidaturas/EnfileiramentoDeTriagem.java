package com.gupyclone.candidaturas;

/**
 * Porta de saida: avisa a triagem que ha uma candidatura nova para analisar.
 *
 * <p>Declarada aqui e implementada na triagem, e e isso que quebra o ciclo
 * entre os dois pacotes: candidaturas pede o enfileiramento sem conhecer fila,
 * job nem tabela de analise.
 */
public interface EnfileiramentoDeTriagem {

    /**
     * Enfileira a analise da candidatura.
     *
     * <p>Chamado <em>dentro</em> da transacao que grava a candidatura (padrao
     * outbox): ou os dois acontecem, ou nenhum.
     */
    void enfileirar(Long candidaturaId);
}
