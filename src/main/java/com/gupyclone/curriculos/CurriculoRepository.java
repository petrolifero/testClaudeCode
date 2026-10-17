package com.gupyclone.curriculos;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CurriculoRepository extends JpaRepository<Curriculo, Long> {

    List<Curriculo> findAllByCandidatoIdOrderByCriadoEmDesc(Long candidatoId);
}
