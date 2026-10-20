package com.gupyclone.seguranca;

/** Papel de quem esta usando o sistema. Vira a authority ROLE_* do Spring Security. */
public enum Papel {
    /** Carlos e Joao: listam vagas, se candidatam e sobem curriculo. */
    CANDIDATO,
    /** Ana: cria vagas e define criterios de aceite. */
    RH;

    public String authority() {
        return "ROLE_" + name();
    }
}
