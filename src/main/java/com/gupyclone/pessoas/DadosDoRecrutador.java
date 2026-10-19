package com.gupyclone.pessoas;

/** Dados de um recrutador vistos de fora do agregado: sem JPA. */
public record DadosDoRecrutador(Long id, String chave, String nome, String email) {
}
