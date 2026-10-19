package com.gupyclone.curriculos;

import java.util.Optional;

/**
 * Porta de leitura de curriculos: a unica travessia permitida de outro
 * agregado para este. Nem {@code Curriculo} nem {@code CurriculoRepository}
 * saem deste pacote.
 */
public interface ConsultaDeCurriculos {

    /** @return vazio se o curriculo nao existir */
    Optional<DadosDoCurriculo> porId(Long id);
}
