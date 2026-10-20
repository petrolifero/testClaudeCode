package com.gupyclone.pessoas;

/** Dados de um candidato vistos de fora do agregado: sem JPA. */
public record DadosDoCandidato(Long id, String chave, String nome, String email) {
}
