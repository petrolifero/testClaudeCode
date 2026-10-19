package com.gupyclone.candidaturas;

/**
 * Uma candidatura vista de fora do agregado: ids e status, sem JPA.
 *
 * <p>E o bastante para a triagem decidir se ha o que analisar e para ela ir
 * buscar, nas portas dos modulos donos, a vaga, o candidato e o curriculo.
 */
public record DadosParaAnalise(Long candidaturaId, Long vagaId, Long candidatoId,
                               Long curriculoId, Long recrutadorId, StatusCandidatura status) {
}
