package com.gupyclone.curriculos;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CurriculoRepository extends JpaRepository<Curriculo, Long> {

    List<Curriculo> findAllByCandidatoIdOrderByCriadoEmDesc(Long candidatoId);

    /**
     * Projecao que atende a porta {@link ConsultaDeCurriculos}.
     *
     * <p>E projecao, e nao {@code findById}, porque a entidade carrega o PDF
     * inteiro em {@code conteudo_pdf} e o texto extraido — nada disso interessa
     * a quem so precisa identificar o curriculo.
     */
    @Query("""
            select new com.gupyclone.curriculos.DadosDoCurriculo(
                       c.id, c.candidatoId, c.nomeArquivo, c.origemTexto, c.paginas)
              from Curriculo c
             where c.id = :id
            """)
    Optional<DadosDoCurriculo> dadosPorId(Long id);
}
