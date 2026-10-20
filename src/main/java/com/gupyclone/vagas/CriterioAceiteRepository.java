package com.gupyclone.vagas;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CriterioAceiteRepository extends JpaRepository<CriterioAceite, Long> {

    List<CriterioAceite> findAllByVagaIdOrderByIdAsc(Long vagaId);
}
