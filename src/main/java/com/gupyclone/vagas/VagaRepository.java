package com.gupyclone.vagas;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface VagaRepository extends JpaRepository<Vaga, Long> {

    List<Vaga> findAllByStatusOrderByCriadoEmDesc(StatusVaga status);

    List<Vaga> findAllByRecrutadorIdOrderByCriadoEmDesc(Long recrutadorId);

    @Query("select v from Vaga v left join fetch v.criterios where v.id = :id")
    Optional<Vaga> buscarComCriterios(Long id);

    /**
     * Projecao que atende a porta {@link ConsultaDeVagas}.
     *
     * <p>E projecao, e nao {@code findById}, porque atravessar um agregado nao
     * justifica trazer a vaga inteira — muito menos os criterios de aceite.
     */
    @Query("""
            select new com.gupyclone.vagas.DadosDaVaga(
                       v.id, v.titulo, v.descricao, v.status, v.pontuacaoMinima, v.recrutador.id)
              from Vaga v
             where v.id = :id
            """)
    Optional<DadosDaVaga> dadosPorId(Long id);
}
