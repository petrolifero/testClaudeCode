package com.gupyclone.seguranca;

import com.gupyclone.pessoas.Candidato;
import com.gupyclone.pessoas.CandidatoRepository;
import com.gupyclone.pessoas.Recrutador;
import com.gupyclone.pessoas.RecrutadorRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolve as personas do enunciado. Nao existe cadastro nem senha: as pessoas
 * ja estao no banco (migracao V2) e clicar em uma delas emite o token.
 */
@Service
@Transactional(readOnly = true)
public class ServicoDePersonas {

    private final CandidatoRepository candidatos;
    private final RecrutadorRepository recrutadores;

    public ServicoDePersonas(CandidatoRepository candidatos, RecrutadorRepository recrutadores) {
        this.candidatos = candidatos;
        this.recrutadores = recrutadores;
    }

    /** Personas oferecidas no cabecalho, RH primeiro e depois candidatos por nome. */
    public List<Persona> disponiveis() {
        List<Persona> personas = new ArrayList<>();
        recrutadores.findAll().stream()
                .sorted(Comparator.comparing(Recrutador::getNome))
                .forEach(rh -> personas.add(new Persona(rh.getChave(), rh.getNome(), Papel.RH)));
        candidatos.findAll().stream()
                .sorted(Comparator.comparing(Candidato::getNome))
                .forEach(candidato -> personas.add(
                        new Persona(candidato.getChave(), candidato.getNome(), Papel.CANDIDATO)));
        return List.copyOf(personas);
    }

    public UsuarioAutenticado resolver(String chave) {
        return recrutadores.findByChave(chave)
                .map(rh -> new UsuarioAutenticado(rh.getChave(), rh.getNome(), Papel.RH, rh.getId()))
                .or(() -> candidatos.findByChave(chave)
                        .map(candidato -> new UsuarioAutenticado(
                                candidato.getChave(), candidato.getNome(), Papel.CANDIDATO, candidato.getId())))
                .orElseThrow(() -> new PersonaInexistenteException(chave));
    }
}
