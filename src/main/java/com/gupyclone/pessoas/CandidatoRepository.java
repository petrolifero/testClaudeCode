package com.gupyclone.pessoas;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CandidatoRepository extends JpaRepository<Candidato, Long> {

    Optional<Candidato> findByChave(String chave);
}
