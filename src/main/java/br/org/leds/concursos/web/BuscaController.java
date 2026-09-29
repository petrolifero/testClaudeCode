package br.org.leds.concursos.web;

import br.org.leds.concursos.aplicacao.BuscaService;
import br.org.leds.concursos.aplicacao.CandidatoCompativel;
import br.org.leds.concursos.aplicacao.ConcursoCompativel;
import br.org.leds.concursos.dominio.CodigoConcurso;
import br.org.leds.concursos.dominio.Cpf;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * API REST com as duas buscas do desafio.
 */
@RestController
@RequestMapping("/api")
public class BuscaController {

    private final BuscaService service;

    public BuscaController(BuscaService service) {
        this.service = service;
    }

    /** Busca 1: concursos compatíveis com o candidato. CPF aceito com ou sem pontuação. */
    @GetMapping("/candidatos/{cpf}/concursos")
    public List<ConcursoCompativel> concursosDoCandidato(@PathVariable String cpf) {
        return service.concursosParaCandidato(Cpf.of(cpf));
    }

    /** Busca 2: candidatos compatíveis com os concursos de um código. */
    @GetMapping("/concursos/{codigo}/candidatos")
    public List<CandidatoCompativel> candidatosDoConcurso(@PathVariable String codigo) {
        return service.candidatosParaConcurso(CodigoConcurso.of(codigo));
    }
}
