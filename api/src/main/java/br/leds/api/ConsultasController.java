package br.leds.api;

import br.leds.comum.aplicacao.ServicoConsultas;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Adaptador HTTP das consultas: traduz requisição em chamada ao caso de uso e resultado em DTO. Sem regra de
 * negócio nem validação (isso é do {@link ServicoConsultas}) e sem tradução de erros (isso é do {@link TratadorErros}).
 */
@RestController
@RequestMapping("/api")
class ConsultasController {

    private final ServicoConsultas consultas;

    ConsultasController(ServicoConsultas consultas) {
        this.consultas = consultas;
    }

    /** Concursos compatíveis com o candidato. O CPF pode vir com ou sem pontuação. */
    @GetMapping("/candidatos/{cpf}/concursos")
    List<ConcursoResposta> concursosDoCandidato(@PathVariable("cpf") String cpf) {
        return consultas.concursosPorCpf(cpf).stream().map(ConcursoResposta::de).toList();
    }

    /** Candidatos compatíveis com os concursos que têm este código. */
    @GetMapping("/concursos/{codigo}/candidatos")
    List<CandidatoResposta> candidatosDoConcurso(@PathVariable("codigo") String codigo) {
        return consultas.candidatosPorConcurso(codigo).stream().map(CandidatoResposta::de).toList();
    }
}
