package com.gupyclone.candidaturas;

import com.gupyclone.comum.ErroDeNegocio;
import org.springframework.http.HttpStatus;

/** O candidato ja tem candidatura nesta vaga. */
public class CandidaturaDuplicadaException extends ErroDeNegocio {

    public CandidaturaDuplicadaException(String tituloDaVaga) {
        super(HttpStatus.CONFLICT, "você já se candidatou à vaga " + tituloDaVaga);
    }
}
