package com.gupyclone.pessoas;

import java.util.Optional;
import org.springframework.stereotype.Component;

/** Porta de pessoas atendida por projecao, sem carregar as entidades. */
// Classe propria em vez de os repositorios implementarem a porta: sao dois
// agregados atras de uma unica interface, e nenhum repositorio precisa sair
// deste pacote para isso.
@Component
public class ConsultaDePessoasJpa implements ConsultaDePessoas {

    private final CandidatoRepository candidatos;
    private final RecrutadorRepository recrutadores;

    public ConsultaDePessoasJpa(CandidatoRepository candidatos, RecrutadorRepository recrutadores) {
        this.candidatos = candidatos;
        this.recrutadores = recrutadores;
    }

    @Override
    public Optional<DadosDoCandidato> candidato(Long id) {
        return candidatos.dadosPorId(id);
    }

    @Override
    public Optional<DadosDoRecrutador> recrutador(Long id) {
        return recrutadores.dadosPorId(id);
    }
}
