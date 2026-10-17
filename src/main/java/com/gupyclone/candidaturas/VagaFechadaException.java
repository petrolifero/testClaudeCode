package com.gupyclone.candidaturas;

import com.gupyclone.comum.ErroDeNegocio;
import org.springframework.http.HttpStatus;

/** A vaga existe, mas nao esta mais recebendo candidaturas. */
public class VagaFechadaException extends ErroDeNegocio {

    public VagaFechadaException(String tituloDaVaga) {
        super(HttpStatus.CONFLICT, "a vaga " + tituloDaVaga + " não está mais recebendo candidaturas");
    }
}
