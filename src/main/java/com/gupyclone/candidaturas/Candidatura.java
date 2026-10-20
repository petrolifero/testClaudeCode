package com.gupyclone.candidaturas;

import com.gupyclone.comum.EntidadeComId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * Relacao entre uma vaga e um candidato, com o curriculo submetido e o RH
 * responsavel (o dono da vaga).
 *
 * <p>Unica por (vaga, candidato) — garantido por constraint no banco.
 *
 * <p>Vaga, candidato, curriculo e recrutador sao <em>outros</em> agregados e
 * entram aqui por id: quem precisa dos dados deles pergunta a porta do modulo
 * dono. As colunas sao as mesmas de antes — muda o mapeamento, nao o esquema.
 */
@Entity
@Table(name = "candidatura")
public class Candidatura extends EntidadeComId {

    // updatable = false nos quatro: de quem e a candidatura e qual curriculo
    // ela submeteu sao fatos do instante da inscricao. O unique
    // (vaga_id, candidato_id) ja diz que trocar isso seria outra candidatura.
    @Column(name = "vaga_id", nullable = false, updatable = false)
    private Long vagaId;

    @Column(name = "candidato_id", nullable = false, updatable = false)
    private Long candidatoId;

    @Column(name = "curriculo_id", nullable = false, updatable = false)
    private Long curriculoId;

    @Column(name = "recrutador_id", nullable = false, updatable = false)
    private Long recrutadorId;

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

    public Candidatura(Long vagaId, Long candidatoId, Long curriculoId, Long recrutadorId) {
        // Recusa aqui, e nao no banco: faltando um id o insert falharia no not
        // null e chegaria a quem chamou como violacao de integridade, que o
        // servico traduz como candidatura duplicada — diagnostico errado.
        if (vagaId == null || candidatoId == null || curriculoId == null || recrutadorId == null) {
            throw new IllegalArgumentException(
                    "candidatura precisa de vaga, candidato, curriculo e recrutador");
        }
        this.vagaId = vagaId;
        this.candidatoId = candidatoId;
        this.curriculoId = curriculoId;
        this.recrutadorId = recrutadorId;
        this.status = StatusCandidatura.RECEBIDA;
    }

    @PrePersist
    void aoPersistir() {
        if (criadoEm == null) {
            criadoEm = OffsetDateTime.now();
        }
    }

    public Long getVagaId() {
        return vagaId;
    }

    public Long getCandidatoId() {
        return candidatoId;
    }

    public Long getCurriculoId() {
        return curriculoId;
    }

    public Long getRecrutadorId() {
        return recrutadorId;
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
