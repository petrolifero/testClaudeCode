package com.gupyclone.triagem;

import com.gupyclone.candidaturas.Candidatura;
import com.gupyclone.comum.AtrasoDeReentrega;
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
        if (status != StatusJob.PROCESSANDO) {
            // Guarda de estado, como a transicao da candidatura ja fazia: um
            // worker atrasado nao reescreve um job que outro ja fechou.
            return;
        }
        this.status = StatusJob.CONCLUIDO;
        this.reivindicadoEm = null;
        this.atualizadoEm = OffsetDateTime.now();
    }

    /**
     * Registra uma falha de processamento.
     *
     * <p>Enquanto houver tentativas o job volta para a fila com atraso
     * EXPONENCIAL. Com atraso fixo, as tentativas se esgotavam em poucos
     * segundos: com espera de 5s e 3 tentativas, uma indisponibilidade de 10
     * segundos da LLM era suficiente para marcar o job como FALHO. O
     * exponencial faz as mesmas 3 tentativas cobrirem minutos.
     *
     * <p>Esgotadas as tentativas o job vira FALHO, de onde a quarentena o
     * devolve mais tarde (ver ReivindicadorDeAnalises): FALHO e um descanso,
     * nao um tumulo.
     *
     * <p>So age sobre job em PROCESSANDO: um worker atrasado, cujo lease ja
     * expirou e cujo job ja foi concluido por outro, nao regride o estado.
     */
    public void registrarFalha(String erro, int maxTentativas, java.time.Duration esperaAposFalha) {
        if (status != StatusJob.PROCESSANDO) {
            return;
        }
        this.ultimoErro = erro;
        this.reivindicadoEm = null;
        this.atualizadoEm = OffsetDateTime.now();
        if (tentativas >= maxTentativas) {
            this.status = StatusJob.FALHO;
        } else {
            this.status = StatusJob.PENDENTE;
            this.disponivelEm = OffsetDateTime.now()
                    .plus(AtrasoDeReentrega.apos(esperaAposFalha, tentativas));
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
