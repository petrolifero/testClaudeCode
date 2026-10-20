package com.gupyclone.candidaturas;

import java.util.Optional;

/**
 * Porta de entrada do agregado: a unica travessia permitida de outro modulo
 * para uma candidatura. Nem {@code Candidatura} nem {@code CandidaturaRepository}
 * saem deste pacote.
 */
public interface Candidaturas {

    /** @return vazio se a candidatura nao existir */
    Optional<DadosParaAnalise> paraAnalise(Long candidaturaId);

    /**
     * Fecha a candidatura com o veredito da triagem.
     *
     * <p>A transicao e idempotente: so muda quem ainda esta em
     * {@code RECEBIDA}. Quem recebe {@code true} e quem deve avisar o
     * candidato, e e so isso que garante um unico aviso por candidatura com
     * varios workers na fila.
     *
     * @return true somente para quem efetivou a transicao (RECEBIDA -> final)
     */
    boolean concluir(Long id, StatusCandidatura status, int pontuacao, String motivo);
}
