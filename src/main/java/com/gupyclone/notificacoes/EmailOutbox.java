package com.gupyclone.notificacoes;

import com.gupyclone.candidaturas.Candidatura;
import com.gupyclone.comum.EntidadeComId;
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
 * E-mail a enviar, gravado na mesma transacao em que a candidatura recebe seu
 * status final (padrao outbox).
 *
 * <p>O unique {@code (candidatura_id, tipo)} e a garantia de nao duplicar: duas
 * tentativas de enfileirar o mesmo aviso colidem no banco.
 */
@Entity
@Table(name = "email_outbox")
public class EmailOutbox extends EntidadeComId {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidatura_id", nullable = false)
    private Candidatura candidatura;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 30)
    private TipoEmail tipo;

    @Column(name = "destinatario", nullable = false, length = 180)
    private String destinatario;

    @Column(name = "assunto", nullable = false, length = 255)
    private String assunto;

    @Column(name = "corpo", nullable = false, columnDefinition = "text")
    private String corpo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StatusEmail status;

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

    @Column(name = "enviado_em")
    private OffsetDateTime enviadoEm;

    protected EmailOutbox() {
    }

    public EmailOutbox(Candidatura candidatura, TipoEmail tipo, String destinatario, String assunto, String corpo) {
        this.candidatura = candidatura;
        this.tipo = tipo;
        this.destinatario = destinatario;
        this.assunto = assunto;
        this.corpo = corpo;
        this.status = StatusEmail.PENDENTE;
        this.tentativas = 0;
        this.disponivelEm = OffsetDateTime.now();
    }

    @PrePersist
    void aoPersistir() {
        OffsetDateTime agora = OffsetDateTime.now();
        if (criadoEm == null) {
            criadoEm = agora;
        }
        if (disponivelEm == null) {
            disponivelEm = agora;
        }
    }

    /** Marca o e-mail como entregue ao servidor SMTP. */
    public void marcarEnviado() {
        this.status = StatusEmail.ENVIADO;
        this.reivindicadoEm = null;
        this.enviadoEm = OffsetDateTime.now();
    }

    /**
     * Registra uma falha de envio. Volta para a fila enquanto houver
     * tentativas; esgotadas, vira FALHO.
     */
    public void registrarFalha(String erro, int maxTentativas, java.time.Duration esperaAposFalha) {
        this.ultimoErro = erro;
        this.reivindicadoEm = null;
        if (tentativas >= maxTentativas) {
            this.status = StatusEmail.FALHO;
        } else {
            this.status = StatusEmail.PENDENTE;
            this.disponivelEm = OffsetDateTime.now().plus(esperaAposFalha);
        }
    }

    public Candidatura getCandidatura() {
        return candidatura;
    }

    public TipoEmail getTipo() {
        return tipo;
    }

    public String getDestinatario() {
        return destinatario;
    }

    public String getAssunto() {
        return assunto;
    }

    public String getCorpo() {
        return corpo;
    }

    public StatusEmail getStatus() {
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

    public OffsetDateTime getEnviadoEm() {
        return enviadoEm;
    }
}
