package com.gupyclone.pessoas;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecrutadorRepository extends JpaRepository<Recrutador, Long> {

    Optional<Recrutador> findByChave(String chave);
}
