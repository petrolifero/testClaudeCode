package com.gupyclone.pessoas;

import java.util.Optional;

/**
 * Porta de leitura de pessoas: a unica travessia permitida de outro agregado
 * para este. Candidato e recrutador sao agregados distintos, com id proprio —
 * um id de candidato nao significa nada do lado do recrutador, e vice-versa.
 */
public interface ConsultaDePessoas {

    /** @return vazio se o candidato nao existir */
    Optional<DadosDoCandidato> candidato(Long id);

    /** @return vazio se o recrutador nao existir */
    Optional<DadosDoRecrutador> recrutador(Long id);
}
