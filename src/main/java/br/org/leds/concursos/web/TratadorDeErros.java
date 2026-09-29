package br.org.leds.concursos.web;

import br.org.leds.concursos.dominio.DadoInvalidoException;
import br.org.leds.concursos.dominio.RecursoNaoEncontradoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Converte exceções em respostas RFC 9457 (Problem Details) com status HTTP adequado.
 */
@RestControllerAdvice
public class TratadorDeErros {

    private static final Logger LOG = LoggerFactory.getLogger(TratadorDeErros.class);

    @ExceptionHandler(DadoInvalidoException.class)
    public ProblemDetail dadoInvalido(DadoInvalidoException e) {
        return problema(HttpStatus.BAD_REQUEST, "Parâmetro inválido", e.getMessage());
    }

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ProblemDetail naoEncontrado(RecursoNaoEncontradoException e) {
        return problema(HttpStatus.NOT_FOUND, "Não encontrado", e.getMessage());
    }

    @ExceptionHandler(DataAccessException.class)
    public ProblemDetail falhaBanco(DataAccessException e) {
        LOG.error("Falha de acesso ao banco de dados", e);
        return problema(HttpStatus.SERVICE_UNAVAILABLE, "Banco de dados indisponível",
                "Não foi possível consultar o banco de dados. Tente novamente mais tarde.");
    }

    private static ProblemDetail problema(HttpStatus status, String titulo, String detalhe) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, detalhe);
        problema.setTitle(titulo);
        return problema;
    }
}
