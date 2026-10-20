package com.gupyclone.vagas;

import com.gupyclone.comum.EntidadeComId;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Vaga publicada pelo RH, com seus criterios de aceite.
 *
 * <p>O recrutador entra por id: ele e outro agregado, e quem precisa do nome
 * dele pergunta a porta de pessoas. Os criterios, ao contrario, sao parte desta
 * vaga e continuam por objeto.
 */
@Entity
@Table(name = "vaga")
public class Vaga extends EntidadeComId {

    @Column(name = "titulo", nullable = false, length = 160)
    private String titulo;

    @Column(name = "descricao", nullable = false, columnDefinition = "text")
    private String descricao;

    @Column(name = "recrutador_id", nullable = false, updatable = false)
    private Long recrutadorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StatusVaga status;

    @Column(name = "pontuacao_minima", nullable = false)
    private int pontuacaoMinima;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private OffsetDateTime criadoEm;

    @OneToMany(mappedBy = "vaga", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<CriterioAceite> criterios = new ArrayList<>();

    protected Vaga() {
    }

    public Vaga(String titulo, String descricao, Long recrutadorId, int pontuacaoMinima) {
        if (pontuacaoMinima < 0 || pontuacaoMinima > 100) {
            throw new IllegalArgumentException("pontuacao minima deve estar entre 0 e 100");
        }
        // Id nulo recusado aqui, e nao so no servico: sem isso o not null da
        // coluna viraria DataIntegrityViolationException no flush, longe de quem
        // errou.
        if (recrutadorId == null) {
            throw new IllegalArgumentException("vaga exige o id do recrutador");
        }
        this.titulo = titulo;
        this.descricao = descricao;
        this.recrutadorId = recrutadorId;
        this.pontuacaoMinima = pontuacaoMinima;
        this.status = StatusVaga.ABERTA;
    }

    @PrePersist
    void aoPersistir() {
        if (criadoEm == null) {
            criadoEm = OffsetDateTime.now();
        }
    }

    public Vaga adicionarCriterio(CriterioAceite criterio) {
        criterio.associarA(this);
        criterios.add(criterio);
        return this;
    }

    public void fechar() {
        this.status = StatusVaga.FECHADA;
    }

    public boolean estaAberta() {
        return status == StatusVaga.ABERTA;
    }

    /** @return true se quem pergunta e quem publicou a vaga */
    public boolean ehDoRecrutador(Long recrutadorId) {
        return this.recrutadorId.equals(recrutadorId);
    }

    public String getTitulo() {
        return titulo;
    }

    public String getDescricao() {
        return descricao;
    }

    public Long getRecrutadorId() {
        return recrutadorId;
    }

    public StatusVaga getStatus() {
        return status;
    }

    public int getPontuacaoMinima() {
        return pontuacaoMinima;
    }

    public OffsetDateTime getCriadoEm() {
        return criadoEm;
    }

    public List<CriterioAceite> getCriterios() {
        return Collections.unmodifiableList(criterios);
    }
}
