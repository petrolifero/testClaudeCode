package com.gupyclone.vagas;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface VagaRepository extends JpaRepository<Vaga, Long> {

    /**
     * O corpo comum das tres leituras que devolvem a vaga inteira: as tres
     * trazem os criterios de aceite no mesmo SELECT.
     *
     * <p>Com {@code open-in-view: false} a vaga chega destacada a quem monta a
     * resposta, e uma colecao preguicosa viraria
     * {@code LazyInitializationException} ali. Era isso que o servico contornava
     * tocando a colecao antes de a transacao fechar, e era isso que custava uma
     * consulta a mais por vaga listada.
     *
     * <p>Sem {@code distinct}: desde o Hibernate 6 o fetch de colecao ja une as
     * linhas repetidas da raiz, e um {@code select distinct} ainda compararia a
     * descricao — coluna {@code text} — linha por linha.
     */
    String COM_CRITERIOS = """
            select v
              from Vaga v
              left join fetch v.criterios c
            """;

    /** A ordem dos criterios e a da publicacao, que e a ordem do id. */
    @Query(COM_CRITERIOS + " where v.id = :id order by c.id")
    Optional<Vaga> buscarComCriterios(Long id);

    @Query(COM_CRITERIOS + " where v.status = :status order by v.criadoEm desc, c.id")
    List<Vaga> buscarPorStatusComCriterios(StatusVaga status);

    @Query(COM_CRITERIOS + " where v.recrutadorId = :recrutadorId order by v.criadoEm desc, c.id")
    List<Vaga> buscarDoRecrutadorComCriterios(Long recrutadorId);

    /**
     * Projecao que atende a porta {@link ConsultaDeVagas}.
     *
     * <p>E projecao, e nao {@code findById}, porque atravessar um agregado nao
     * justifica trazer a vaga inteira — muito menos os criterios de aceite.
     */
    @Query("""
            select new com.gupyclone.vagas.DadosDaVaga(
                       v.id, v.titulo, v.descricao, v.status, v.pontuacaoMinima, v.recrutadorId)
              from Vaga v
             where v.id = :id
            """)
    Optional<DadosDaVaga> dadosPorId(Long id);
}
