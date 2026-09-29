package br.org.leds.concursos.aplicacao;

import br.org.leds.concursos.dominio.CodigoConcurso;
import br.org.leds.concursos.dominio.Cpf;
import br.org.leds.concursos.dominio.RecursoNaoEncontradoException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Casos de uso das duas buscas exigidas pelo desafio.
 */
@Service
@Transactional(readOnly = true)
public class BuscaService {

    private final BuscaRepository repository;

    public BuscaService(BuscaRepository repository) {
        this.repository = repository;
    }

    /**
     * Busca 1: órgãos, códigos e editais dos concursos que se encaixam no perfil do candidato.
     *
     * @throws RecursoNaoEncontradoException se não houver candidato com o CPF
     */
    public List<ConcursoCompativel> concursosParaCandidato(Cpf cpf) {
        if (!repository.existeCandidato(cpf)) {
            throw new RecursoNaoEncontradoException("Candidato não encontrado para o CPF " + cpf);
        }
        return repository.concursosCompativeis(cpf);
    }

    /**
     * Busca 2: nome, data de nascimento e CPF dos candidatos que se encaixam no perfil do concurso.
     *
     * @throws RecursoNaoEncontradoException se não houver concurso com o código
     */
    public List<CandidatoCompativel> candidatosParaConcurso(CodigoConcurso codigo) {
        if (!repository.existeConcurso(codigo)) {
            throw new RecursoNaoEncontradoException("Concurso não encontrado para o código " + codigo);
        }
        return repository.candidatosCompativeis(codigo);
    }
}
