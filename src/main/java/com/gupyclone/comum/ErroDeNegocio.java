package com.gupyclone.comum;

import org.springframework.http.HttpStatus;

/**
 * Base das falhas previsiveis de regra de negocio.
 *
 * <p>Toda excecao de dominio do projeto estende esta classe e declara o status
 * HTTP que lhe corresponde, para que exista um unico tradutor de erros
 * ({@code TratadorDeErros}) em vez de um catch por controlador.
 */
public abstract class ErroDeNegocio extends RuntimeException {

    private final HttpStatus status;

    protected ErroDeNegocio(HttpStatus status, String mensagem) {
        super(mensagem);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
