package com.gupyclone.candidaturas;

import com.gupyclone.curriculos.OrigemTexto;
import java.time.OffsetDateTime;

/**
 * Uma candidatura como a API e as paginas a mostram.
 *
 * <p>Fica no pacote de dominio, e nao dentro do controlador, porque existem
 * duas formas de obte-la e as duas sao legitimas: de uma entidade ja carregada
 * ({@link #de}) ou direto do banco por projecao
 * ({@link CandidaturaRepository#resumosDoCandidato}).
 *
 * <p>A projecao existe por um motivo medido: {@code Curriculo} guarda o PDF em
 * {@code bytea} e o texto extraido, e {@code byte[]} nao e preguicoso sem
 * enhancement de bytecode. Inicializar a associacao para ler um unico enum
 * trazia o PDF inteiro e centenas de milhares de caracteres para a memoria, em
 * TODA listagem — com dezenas de candidaturas isso e dezenas de PDFs em heap
 * de uma vez.
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

    public static CandidaturaResposta de(Candidatura candidatura) {
        return new CandidaturaResposta(
                candidatura.getId(),
                candidatura.getVaga().getId(),
                candidatura.getVaga().getTitulo(),
                candidatura.getCandidato().getId(),
                candidatura.getCandidato().getNome(),
                candidatura.getStatus(),
                candidatura.getPontuacao(),
                candidatura.getMotivo(),
                candidatura.getCurriculo().getOrigemTexto(),
                candidatura.getCriadoEm(),
                candidatura.getAnalisadaEm());
    }
}
