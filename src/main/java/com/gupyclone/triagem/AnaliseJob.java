package com.gupyclone.triagem;

import com.gupyclone.candidaturas.Candidatura;
import com.gupyclone.comum.EntidadeComId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * Item da fila de analise, uma linha por candidatura.
 *
 * <p>Criado na mesma transacao da candidatura (padrao outbox): nao existe
 * candidatura submetida sem job de analise, e nao existe job duplicado porque
 * {@code candidatura_id} e unique.
 */
@Entity
@Table(name = "analise_job")
public class AnaliseJob extends EntidadeComId {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidatura_id", nullable = false, unique = true)
    private Candidatura candidatura;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StatusJob status;

    @Column(name = "tentativas", nullable = false)
    private int tentativas;

    @Column(name = "ultimo_erro", columnDefinition = "text")
    private String ultimoErro;

    @Column(name = "disponivel_em", nullable = false)
    private OffsetDateTime disponivelEm;

    @Column(name = "reivindicado_em")
    private OffsetDateTime reivindicadoEm;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private OffsetDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private OffsetDateTime atualizadoEm;

    protected AnaliseJob() {
    }

    public AnaliseJob(Candidatura candidatura) {
        this.candidatura = candidatura;
        this.status = StatusJob.PENDENTE;
        this.tentativas = 0;
        this.disponivelEm = OffsetDateTime.now();
    }

    @PrePersist
    void aoPersistir() {
        OffsetDateTime agora = OffsetDateTime.now();
        if (criadoEm == null) {
            criadoEm = agora;
        }
        if (atualizadoEm == null) {
            atualizadoEm = agora;
        }
        if (disponivelEm == null) {
            disponivelEm = agora;
        }
    }

    /**
     * Marca o job como concluido. Chamado na transacao que ja gravou o
     * resultado da analise.
     */
    public void concluir() {
        this.status = StatusJob.CONCLUIDO;
        this.reivindicadoEm = null;
        this.atualizadoEm = OffsetDateTime.now();
    }

    /**
     * Registra uma falha de processamento. Enquanto houver tentativas o job
     * volta para a fila com um atraso; esgotadas as tentativas ele vira FALHO
     * e deixa de ser reprocessado (mas permanece na tabela para auditoria).
     */
    public void registrarFalha(String erro, int maxTentativas, java.time.Duration esperaAposFalha) {
        this.ultimoErro = erro;
        this.reivindicadoEm = null;
        this.atualizadoEm = OffsetDateTime.now();
        if (tentativas >= maxTentativas) {
            this.status = StatusJob.FALHO;
        } else {
            this.status = StatusJob.PENDENTE;
            this.disponivelEm = OffsetDateTime.now().plus(esperaAposFalha);
        }
    }

    public Candidatura getCandidatura() {
        return candidatura;
    }

    public StatusJob getStatus() {
        return status;
    }

    public int getTentativas() {
        return tentativas;
    }

    public String getUltimoErro() {
        return ultimoErro;
    }

    public OffsetDateTime getDisponivelEm() {
        return disponivelEm;
    }

    public OffsetDateTime getReivindicadoEm() {
        return reivindicadoEm;
    }

    public OffsetDateTime getCriadoEm() {
        return criadoEm;
    }

    public OffsetDateTime getAtualizadoEm() {
        return atualizadoEm;
    }
}
