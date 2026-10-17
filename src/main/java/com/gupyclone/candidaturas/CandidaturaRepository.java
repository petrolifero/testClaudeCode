package com.gupyclone.candidaturas;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CandidaturaRepository extends JpaRepository<Candidatura, Long> {

    boolean existsByVagaIdAndCandidatoId(Long vagaId, Long candidatoId);

    Optional<Candidatura> findByVagaIdAndCandidatoId(Long vagaId, Long candidatoId);

    List<Candidatura> findAllByCandidatoIdOrderByCriadoEmDesc(Long candidatoId);

    List<Candidatura> findAllByVagaIdOrderByCriadoEmDesc(Long vagaId);

    List<Candidatura> findAllByStatus(StatusCandidatura status);

    /**
     * Transicao de estado idempotente: so muda quem ainda esta em RECEBIDA.
     *
     * <p>Retorna 1 quando esta chamada foi a que concluiu a candidatura e 0
     * quando outra execucao chegou primeiro. E esse retorno que o processador
     * de analises usa para decidir se deve enfileirar o e-mail, o que garante
     * exatamente um e-mail por candidatura mesmo com varios workers.
     *
     * @return numero de linhas afetadas (0 ou 1)
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Candidatura c
               set c.status = :novoStatus,
                   c.pontuacao = :pontuacao,
                   c.motivo = :motivo,
                   c.analisadaEm = :analisadaEm
             where c.id = :id
               and c.status = com.gupyclone.candidaturas.StatusCandidatura.RECEBIDA
            """)
    int concluir(@Param("id") Long id,
                 @Param("novoStatus") StatusCandidatura novoStatus,
                 @Param("pontuacao") Integer pontuacao,
                 @Param("motivo") String motivo,
                 @Param("analisadaEm") OffsetDateTime analisadaEm);
}
