package com.gupyclone.triagem;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnaliseJobRepository extends JpaRepository<AnaliseJob, Long> {

    Optional<AnaliseJob> findByCandidaturaId(Long candidaturaId);

    List<AnaliseJob> findAllByStatus(StatusJob status);

    long countByStatus(StatusJob status);
}
