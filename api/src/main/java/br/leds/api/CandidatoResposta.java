package br.leds.api;

import br.leds.comum.dominio.CandidatoEncontrado;
import br.leds.comum.dominio.Cpf;
import java.time.LocalDate;

/** Contrato HTTP de um candidato: data em ISO-8601 (serializada por Jackson) e CPF formatado. */
record CandidatoResposta(String nome, LocalDate dataNascimento, String cpf) {

    static CandidatoResposta de(CandidatoEncontrado candidato) {
        return new CandidatoResposta(candidato.nome(), candidato.dataNascimento(), Cpf.formatar(candidato.cpf()));
    }
}
