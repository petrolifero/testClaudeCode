package com.gupyclone.vagas;

/**
 * Dados de uma vaga vistos de fora do agregado: ids e valores, sem JPA.
 *
 * <p>Nao traz os criterios de aceite de proposito: eles sao parte interna da
 * {@link Vaga} e nenhum outro agregado tem o que fazer com eles.
 */
public record DadosDaVaga(Long id, String titulo, String descricao,
                          StatusVaga status, int pontuacaoMinima, Long recrutadorId) {

    public boolean estaAberta() {
        return status == StatusVaga.ABERTA;
    }
}
