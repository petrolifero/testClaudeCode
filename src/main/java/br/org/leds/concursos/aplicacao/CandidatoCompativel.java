package br.org.leds.concursos.aplicacao;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;

/**
 * Resultado da busca 2: candidato que se encaixa no perfil de um concurso.
 * A data é serializada no mesmo formato dos arquivos de entrada ({@code dd/MM/yyyy}).
 */
public record CandidatoCompativel(
        String nome,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd/MM/yyyy") LocalDate dataNascimento,
        String cpf) {
}
