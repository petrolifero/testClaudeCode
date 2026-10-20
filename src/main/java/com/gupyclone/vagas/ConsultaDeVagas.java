package com.gupyclone.vagas;

import java.util.Optional;

/**
 * Porta de leitura de vagas: a unica travessia permitida de outro agregado
 * para este. Nem {@code Vaga} nem {@code VagaRepository} saem deste pacote.
 */
public interface ConsultaDeVagas {

    /** @return vazio se a vaga nao existir */
    Optional<DadosDaVaga> porId(Long id);
}
