package com.gupyclone.seguranca;

/** Nao existe persona com a chave informada. */
public class PersonaInexistenteException extends RuntimeException {

    public PersonaInexistenteException(String chave) {
        super("nao existe persona com a chave '" + chave + "'");
    }
}
