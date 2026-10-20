package com.gupyclone.candidaturas;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CandidaturaRepository extends JpaRepository<Candidatura, Long> {

    /**
     * O corpo comum das tres consultas de resumo: uma por candidatura, uma por
     * candidato e uma por vaga, com a mesma forma de resposta.
     *
     * <p>A resposta e um modelo de leitura e junta <em>por id</em> as tres
     * colunas que a tabela de candidatura nao tem (titulo da vaga, nome do
     * candidato e origem do texto) numa consulta so. A alternativa seria uma
     * ida a porta de cada modulo dono por linha exibida.
     *
     * <p>Do curriculo le apenas a origem do texto, por um custo medido:
     * {@code byte[]} nao e preguicoso sem enhancement de bytecode, entao
     * carregar o curriculo para ler um unico enum trazia o PDF inteiro, e o
     * texto extraido, para a memoria em TODA listagem.
     */
    String RESUMO = """
            select new com.gupyclone.candidaturas.CandidaturaResposta(
                       c.id, c.vagaId, v.titulo, c.candidatoId, p.nome,
                       c.status, c.pontuacao, c.motivo, cu.origemTexto,
                       c.criadoEm, c.analisadaEm)
              from Candidatura c
              join Vaga v on v.id = c.vagaId
              join Candidato p on p.id = c.candidatoId
              join Curriculo cu on cu.id = c.curriculoId
            """;

    boolean existsByVagaIdAndCandidatoId(Long vagaId, Long candidatoId);

    /**
     * Candidaturas num estado. Nao e chamada em producao: existe para as
     * asserticoes dos testes de concorrencia, que precisam afirmar "nenhuma
     * ficou em RECEBIDA". Fica porque e consulta legitima do agregado, nao
     * atalho para fora dele.
     */
    List<Candidatura> findAllByStatus(StatusCandidatura status);

    /** Projecao que atende a porta {@link Candidaturas#paraAnalise}. */
    @Query("""
            select new com.gupyclone.candidaturas.DadosParaAnalise(
                       c.id, c.vagaId, c.candidatoId, c.curriculoId, c.recrutadorId, c.status)
              from Candidatura c
             where c.id = :id
            """)
    Optional<DadosParaAnalise> dadosParaAnalise(@Param("id") Long id);

    /**
     * A candidatura recem-gravada, como a API a devolve no 201.
     *
     * <p>Por projecao, e nao pela entidade: a resposta precisa do titulo da
     * vaga, do nome do candidato e da origem do texto, que vivem em outros
     * agregados — com a entidade em mao nao haveria o que ler.
     */
    @Query(RESUMO + " where c.id = :id")
    Optional<CandidaturaResposta> resumoPorId(@Param("id") Long id);

    @Query(RESUMO + " where c.candidatoId = :candidatoId order by c.criadoEm desc")
    List<CandidaturaResposta> resumosDoCandidato(@Param("candidatoId") Long candidatoId);

    @Query(RESUMO + " where c.vagaId = :vagaId order by c.criadoEm desc")
    List<CandidaturaResposta> resumosDaVaga(@Param("vagaId") Long vagaId);

    /**
     * Transicao de estado idempotente: so muda quem ainda esta em RECEBIDA.
     *
     * <p>Retorna 1 quando esta chamada foi a que concluiu a candidatura e 0
     * quando outra execucao chegou primeiro. E esse retorno que a porta
     * {@link Candidaturas#concluir} transforma no booleano com que a triagem
     * decide se enfileira o e-mail, o que garante exatamente um e-mail por
     * candidatura mesmo com varios workers.
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
