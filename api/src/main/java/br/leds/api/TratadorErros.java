package br.leds.api;

import br.leds.comum.erro.EntradaInvalida;
import br.leds.comum.erro.ErroAplicacao;
import br.leds.comum.erro.ErroInfraestrutura;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Traduz a hierarquia de {@link ErroAplicacao} em respostas RFC 7807 (application/problem+json). */
@RestControllerAdvice
class TratadorErros {

    private static final Logger LOG = LoggerFactory.getLogger(TratadorErros.class);

    @ExceptionHandler(EntradaInvalida.class)
    ProblemDetail entradaInvalida(EntradaInvalida e) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        problema.setTitle("Entrada inválida");
        return problema;
    }

    /** A mensagem original (pode citar URL/SQL do banco) vai só para o log, nunca para o cliente. */
    @ExceptionHandler(ErroInfraestrutura.class)
    ProblemDetail infraestrutura(ErroInfraestrutura e) {
        LOG.error("Falha de infraestrutura", e);
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE, "Serviço de dados temporariamente indisponível.");
        problema.setTitle("Serviço indisponível");
        return problema;
    }

    @ExceptionHandler(ErroAplicacao.class)
    ProblemDetail generico(ErroAplicacao e) {
        LOG.error("Erro inesperado da aplicação", e);
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno.");
        problema.setTitle("Erro interno");
        return problema;
    }
}
