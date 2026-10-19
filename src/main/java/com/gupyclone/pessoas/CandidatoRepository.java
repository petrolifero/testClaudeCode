package com.gupyclone.pessoas;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CandidatoRepository extends JpaRepository<Candidato, Long> {

    Optional<Candidato> findByChave(String chave);

    /** Projecao que atende a porta {@link ConsultaDePessoas}. */
    @Query("""
            select new com.gupyclone.pessoas.DadosDoCandidato(c.id, c.chave, c.nome, c.email)
              from Candidato c
             where c.id = :id
            """)
    Optional<DadosDoCandidato> dadosPorId(Long id);
}
