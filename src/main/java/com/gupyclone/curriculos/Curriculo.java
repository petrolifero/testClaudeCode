package com.gupyclone.curriculos;

import com.gupyclone.comum.EntidadeComId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * Curriculo em PDF de um candidato, com o texto ja extraido.
 *
 * <p>O candidato entra por id: ele e outro agregado, e quem precisa do nome
 * dele pergunta a porta de pessoas.
 *
 * <p>A coluna {@code texto_busca} do banco e uma coluna gerada
 * ({@code to_tsvector('portugues_sem_acento', texto)}) e por isso nao e
 * mapeada aqui: quem a consulta e o analisador textual, via SQL nativo.
 */
@Entity
@Table(name = "curriculo")
public class Curriculo extends EntidadeComId {

    @Column(name = "candidato_id", nullable = false, updatable = false)
    private Long candidatoId;

    @Column(name = "nome_arquivo", nullable = false, length = 255)
    private String nomeArquivo;

    @Column(name = "conteudo_pdf", nullable = false)
    private byte[] conteudoPdf;

    @Column(name = "texto", nullable = false, columnDefinition = "text")
    private String texto;

    @Enumerated(EnumType.STRING)
    @Column(name = "origem_texto", nullable = false, length = 20)
    private OrigemTexto origemTexto;

    @Column(name = "paginas", nullable = false)
    private int paginas;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private OffsetDateTime criadoEm;

    protected Curriculo() {
    }

    public Curriculo(Long candidatoId, String nomeArquivo, byte[] conteudoPdf, TextoExtraido extraido) {
        // Id nulo recusado aqui, e nao so no servico: sem isso o not null da
        // coluna viraria DataIntegrityViolationException no flush, longe de quem
        // errou.
        if (candidatoId == null) {
            throw new IllegalArgumentException("curriculo exige o id do candidato");
        }
        this.candidatoId = candidatoId;
        this.nomeArquivo = nomeArquivo;
        this.conteudoPdf = conteudoPdf;
        this.texto = extraido.texto();
        this.origemTexto = extraido.origem();
        this.paginas = extraido.paginas();
    }

    @PrePersist
    void aoPersistir() {
        if (criadoEm == null) {
            criadoEm = OffsetDateTime.now();
        }
    }

    public Long getCandidatoId() {
        return candidatoId;
    }

    public String getNomeArquivo() {
        return nomeArquivo;
    }

    public byte[] getConteudoPdf() {
        return conteudoPdf;
    }

    public String getTexto() {
        return texto;
    }

    public OrigemTexto getOrigemTexto() {
        return origemTexto;
    }

    public int getPaginas() {
        return paginas;
    }

    public OffsetDateTime getCriadoEm() {
        return criadoEm;
    }
}
