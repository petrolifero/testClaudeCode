package com.gupyclone.candidaturas;

import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CandidaturaRepository extends JpaRepository<Candidatura, Long> {

    boolean existsByVagaIdAndCandidatoId(Long vagaId, Long candidatoId);

    /**
     * Candidaturas num estado. Nao e chamada em producao: existe para as
     * asserticoes dos testes de concorrencia, que precisam afirmar "nenhuma
     * ficou em RECEBIDA". Fica porque e consulta legitima do agregado, nao
     * atalho para fora dele.
     */
    List<Candidatura> findAllByStatus(StatusCandidatura status);

    /**
     * Listagem por projecao: do curriculo le apenas a origem do texto, sem
     * trazer o PDF em bytea nem o texto extraido para a memoria.
     *
     * <p>Existe por um custo medido: byte[] nao e preguicoso sem enhancement
     * de bytecode, entao inicializar a associacao do curriculo para ler um
     * unico enum carregava o PDF inteiro em TODA listagem.
     */
    @Query("""
            select new com.gupyclone.candidaturas.CandidaturaResposta(
                       c.id, c.vaga.id, c.vaga.titulo, c.candidato.id, c.candidato.nome,
                       c.status, c.pontuacao, c.motivo, c.curriculo.origemTexto,
                       c.criadoEm, c.analisadaEm)
              from Candidatura c
             where c.candidato.id = :candidatoId
             order by c.criadoEm desc
            """)
    List<CandidaturaResposta> resumosDoCandidato(@Param("candidatoId") Long candidatoId);

    @Query("""
            select new com.gupyclone.candidaturas.CandidaturaResposta(
                       c.id, c.vaga.id, c.vaga.titulo, c.candidato.id, c.candidato.nome,
                       c.status, c.pontuacao, c.motivo, c.curriculo.origemTexto,
                       c.criadoEm, c.analisadaEm)
              from Candidatura c
             where c.vaga.id = :vagaId
             order by c.criadoEm desc
            """)
    List<CandidaturaResposta> resumosDaVaga(@Param("vagaId") Long vagaId);

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
