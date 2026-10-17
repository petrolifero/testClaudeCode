package com.gupyclone.candidaturas;

import com.gupyclone.comum.EntidadeComId;
import com.gupyclone.curriculos.Curriculo;
import com.gupyclone.pessoas.Candidato;
import com.gupyclone.pessoas.Recrutador;
import com.gupyclone.vagas.Vaga;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * Relacao entre uma vaga e um candidato, com o curriculo submetido e o RH
 * responsavel (o dono da vaga).
 *
 * <p>Unica por (vaga, candidato) — garantido por constraint no banco.
 */
@Entity
@Table(name = "candidatura")
public class Candidatura extends EntidadeComId {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vaga_id", nullable = false)
    private Vaga vaga;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidato_id", nullable = false)
    private Candidato candidato;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "curriculo_id", nullable = false)
    private Curriculo curriculo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recrutador_id", nullable = false)
    private Recrutador recrutador;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StatusCandidatura status;

    @Column(name = "pontuacao")
    private Integer pontuacao;

    @Column(name = "motivo", columnDefinition = "text")
    private String motivo;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private OffsetDateTime criadoEm;

    @Column(name = "analisada_em")
    private OffsetDateTime analisadaEm;

    protected Candidatura() {
    }

    public Candidatura(Vaga vaga, Candidato candidato, Curriculo curriculo) {
        this.vaga = vaga;
        this.candidato = candidato;
        this.curriculo = curriculo;
        this.recrutador = vaga.getRecrutador();
        this.status = StatusCandidatura.RECEBIDA;
    }

    @PrePersist
    void aoPersistir() {
        if (criadoEm == null) {
            criadoEm = OffsetDateTime.now();
        }
    }

    public Vaga getVaga() {
        return vaga;
    }

    public Candidato getCandidato() {
        return candidato;
    }

    public Curriculo getCurriculo() {
        return curriculo;
    }

    public Recrutador getRecrutador() {
        return recrutador;
    }

    public StatusCandidatura getStatus() {
        return status;
    }

    public Integer getPontuacao() {
        return pontuacao;
    }

    public String getMotivo() {
        return motivo;
    }

    public OffsetDateTime getCriadoEm() {
        return criadoEm;
    }

    public OffsetDateTime getAnalisadaEm() {
        return analisadaEm;
    }
}
