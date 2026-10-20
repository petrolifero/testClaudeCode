package com.gupyclone.candidaturas;

/**
 * Ciclo de vida de uma candidatura.
 *
 * <p>{@code RECEBIDA} e o unico estado de onde se sai, e a saida e definitiva.
 * Essa forma (um unico estado de origem) e o que permite a transicao idempotente
 * {@code update ... where id = ? and status = 'RECEBIDA'}: dois workers que
 * tentem concluir a mesma candidatura resultam em uma unica mudanca de estado.
 */
public enum StatusCandidatura {
    RECEBIDA,
    APROVADA,
    REPROVADA;

    public boolean ehFinal() {
        return this != RECEBIDA;
    }
}
