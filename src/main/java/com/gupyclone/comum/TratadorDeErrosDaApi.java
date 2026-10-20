package com.gupyclone.comum;

import com.gupyclone.curriculos.CurriculoInvalidoException;
import com.gupyclone.seguranca.JaAutenticadoException;
import com.gupyclone.seguranca.PersonaInexistenteException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduz excecoes em respostas JSON.
 *
 * <p>Restrito a {@code @RestController} de proposito: as paginas Thymeleaf
 * continuam com o tratamento de erro do Spring MVC em vez de receberem JSON.
 */
@RestControllerAdvice(annotations = RestController.class)
public class TratadorDeErrosDaApi {

    private static final Logger log = LoggerFactory.getLogger(TratadorDeErrosDaApi.class);

    /** Corpo de erro da API. */
    public record Erro(String codigo, String mensagem) {
    }

    @ExceptionHandler(ErroDeNegocio.class)
    public ResponseEntity<Erro> erroDeNegocio(ErroDeNegocio e) {
        return resposta(e.getStatus(), e);
    }

    @ExceptionHandler(JaAutenticadoException.class)
    public ResponseEntity<Erro> jaAutenticado(JaAutenticadoException e) {
        return resposta(HttpStatus.CONFLICT, e);
    }

    @ExceptionHandler(PersonaInexistenteException.class)
    public ResponseEntity<Erro> personaInexistente(PersonaInexistenteException e) {
        return resposta(HttpStatus.NOT_FOUND, e);
    }

    @ExceptionHandler(CurriculoInvalidoException.class)
    public ResponseEntity<Erro> curriculoInvalido(CurriculoInvalidoException e) {
        return resposta(HttpStatus.UNPROCESSABLE_ENTITY, e);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Erro> acessoNegado(AccessDeniedException e) {
        return resposta(HttpStatus.FORBIDDEN, e);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Erro> dadosInvalidos(MethodArgumentNotValidException e) {
        String detalhe = e.getBindingResult().getFieldErrors().stream()
                .map(erro -> erro.getField() + ": " + erro.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("dados invalidos");
        return ResponseEntity.badRequest().body(new Erro("dados_invalidos", detalhe));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Erro> argumentoInvalido(IllegalArgumentException e) {
        return resposta(HttpStatus.BAD_REQUEST, e);
    }

    private ResponseEntity<Erro> resposta(HttpStatus status, Exception e) {
        if (status.is5xxServerError()) {
            log.error("falha ao atender requisicao", e);
        } else {
            log.debug("requisicao recusada: {}", e.getMessage());
        }
        String codigo = e.getClass().getSimpleName()
                .replaceAll("Exception$", "")
                .replaceAll("(?<!^)(?=[A-Z])", "_")
                .toLowerCase();
        return ResponseEntity.status(status).body(new Erro(codigo, e.getMessage()));
    }
}
