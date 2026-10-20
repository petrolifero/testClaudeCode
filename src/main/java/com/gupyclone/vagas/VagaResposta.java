package com.gupyclone.vagas;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Uma vaga como a API e as paginas a mostram.
 *
 * <p>Fica no pacote de dominio, e nao dentro do controlador, porque montar uma
 * exige sair do agregado: o nome de quem publicou nao esta na vaga, que guarda
 * apenas o id do recrutador. Por isso as duas fabricas sao de pacote e quem le
 * de fora passa pelo {@link MontadorDeVagaResposta}, que resolve o nome pela
 * porta de pessoas.
 */
public record VagaResposta(Long id,
                           String titulo,
                           String descricao,
                           StatusVaga status,
                           int pontuacaoMinima,
                           RecrutadorResposta recrutador,
                           List<CriterioResposta> criterios,
                           OffsetDateTime criadoEm) {

    /** Quem publicou a vaga, o suficiente para exibir. */
    public record RecrutadorResposta(Long id, String nome) {
    }

    /** Criterio de aceite ja gravado. */
    public record CriterioResposta(Long id, String descricao, String expressao, int peso, boolean obrigatorio) {
    }

    /**
     * Versao para quem nao e o RH dono da vaga: sem a expressao de busca.
     *
     * <p>A expressao e o criterio de triagem em forma executavel — inclui
     * negativas como {@code -php}. Entregar isso ao candidato e dizer
     * exatamente o que escrever no curriculo para passar, e a interface
     * nunca a mostrou; era a API que vazava.
     */
    static VagaResposta semExpressoes(Vaga vaga, String nomeDoRecrutador) {
        VagaResposta completa = de(vaga, nomeDoRecrutador);
        return new VagaResposta(completa.id(), completa.titulo(), completa.descricao(),
                completa.status(), completa.pontuacaoMinima(), completa.recrutador(),
                completa.criterios().stream()
                        .map(criterio -> new CriterioResposta(criterio.id(), criterio.descricao(),
                                null, criterio.peso(), criterio.obrigatorio()))
                        .toList(),
                completa.criadoEm());
    }

    static VagaResposta de(Vaga vaga, String nomeDoRecrutador) {
        return new VagaResposta(
                vaga.getId(),
                vaga.getTitulo(),
                vaga.getDescricao(),
                vaga.getStatus(),
                vaga.getPontuacaoMinima(),
                new RecrutadorResposta(vaga.getRecrutadorId(), nomeDoRecrutador),
                vaga.getCriterios().stream()
                        .map(criterio -> new CriterioResposta(criterio.getId(), criterio.getDescricao(),
                                criterio.getExpressao(), criterio.getPeso(), criterio.isObrigatorio()))
                        .toList(),
                vaga.getCriadoEm());
    }
}
