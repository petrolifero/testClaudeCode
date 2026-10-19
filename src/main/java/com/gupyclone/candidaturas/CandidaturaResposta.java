package com.gupyclone.candidaturas;

import com.gupyclone.curriculos.OrigemTexto;
import java.time.OffsetDateTime;

/**
 * Uma candidatura como a API e as paginas a mostram.
 *
 * <p>Fica no pacote de dominio, e nao dentro do controlador, porque as tres
 * consultas que a produzem sao do agregado (ver {@link CandidaturaRepository}).
 *
 * <p>Vem sempre de projecao, nunca de uma {@code Candidatura} em mao: titulo da
 * vaga, nome do candidato e origem do texto pertencem a outros agregados, e a
 * candidatura so guarda os ids deles.
 */
public record CandidaturaResposta(Long id,
                                  Long vagaId,
                                  String tituloDaVaga,
                                  Long candidatoId,
                                  String nomeDoCandidato,
                                  StatusCandidatura status,
                                  Integer pontuacao,
                                  String motivo,
                                  OrigemTexto origemDoTexto,
                                  OffsetDateTime criadoEm,
                                  OffsetDateTime analisadaEm) {
}
