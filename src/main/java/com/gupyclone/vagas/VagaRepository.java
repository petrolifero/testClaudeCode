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
}
