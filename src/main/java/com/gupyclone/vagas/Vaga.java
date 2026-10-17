package com.gupyclone.vagas;

import com.gupyclone.comum.EntidadeComId;
import com.gupyclone.pessoas.Recrutador;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Vaga publicada pelo RH, com seus criterios de aceite. */
@Entity
@Table(name = "vaga")
public class Vaga extends EntidadeComId {

    @Column(name = "titulo", nullable = false, length = 160)
    private String titulo;

    @Column(name = "descricao", nullable = false, columnDefinition = "text")
    private String descricao;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recrutador_id", nullable = false)
    private Recrutador recrutador;

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

    public Vaga(String titulo, String descricao, Recrutador recrutador, int pontuacaoMinima) {
        if (pontuacaoMinima < 0 || pontuacaoMinima > 100) {
            throw new IllegalArgumentException("pontuacao minima deve estar entre 0 e 100");
        }
        this.titulo = titulo;
        this.descricao = descricao;
        this.recrutador = recrutador;
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

    public String getTitulo() {
        return titulo;
    }

    public String getDescricao() {
        return descricao;
    }

    public Recrutador getRecrutador() {
        return recrutador;
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
