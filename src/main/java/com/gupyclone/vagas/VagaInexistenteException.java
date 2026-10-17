package com.gupyclone.vagas;

import com.gupyclone.comum.ErroDeNegocio;
import org.springframework.http.HttpStatus;

/** Nao existe vaga com o id informado. */
public class VagaInexistenteException extends ErroDeNegocio {

    public VagaInexistenteException(Long id) {
        super(HttpStatus.NOT_FOUND, "não existe vaga com o id " + id);
    }
}
