package br.org.leds.concursos.dominio;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;

/**
 * Candidato lido do arquivo {@code candidatos.txt}.
 *
 * @param nome           nome completo
 * @param dataNascimento data de nascimento
 * @param cpf            CPF (identificador único do candidato)
 * @param profissoes     profissões do candidato (não vazia)
 */
public record Candidato(String nome, LocalDate dataNascimento, Cpf cpf, Set<String> profissoes) {

    public Candidato {
        Objects.requireNonNull(cpf, "cpf");
        Objects.requireNonNull(dataNascimento, "dataNascimento");
        if (nome == null || nome.isBlank()) {
            throw new DadoInvalidoException("Nome do candidato é obrigatório (CPF " + cpf + ")");
        }
        if (profissoes == null || profissoes.isEmpty()) {
            throw new DadoInvalidoException("Candidato sem profissões (CPF " + cpf + ")");
        }
        nome = nome.strip();
        profissoes = Set.copyOf(profissoes);
    }
}
