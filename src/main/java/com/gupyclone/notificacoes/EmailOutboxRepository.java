package com.gupyclone.notificacoes;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailOutboxRepository extends JpaRepository<EmailOutbox, Long> {

    List<EmailOutbox> findAllByCandidaturaId(Long candidaturaId);

    Optional<EmailOutbox> findByCandidaturaIdAndTipo(Long candidaturaId, TipoEmail tipo);

    List<EmailOutbox> findAllByStatus(StatusEmail status);

    long countByStatus(StatusEmail status);

    long countByCandidaturaId(Long candidaturaId);
}
