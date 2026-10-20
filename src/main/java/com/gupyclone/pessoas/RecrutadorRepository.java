package com.gupyclone.pessoas;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RecrutadorRepository extends JpaRepository<Recrutador, Long> {

    Optional<Recrutador> findByChave(String chave);

    /** Projecao que atende a porta {@link ConsultaDePessoas}. */
    @Query("""
            select new com.gupyclone.pessoas.DadosDoRecrutador(r.id, r.chave, r.nome, r.email)
              from Recrutador r
             where r.id = :id
            """)
    Optional<DadosDoRecrutador> dadosPorId(Long id);
}
