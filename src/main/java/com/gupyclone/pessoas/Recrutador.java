package com.gupyclone.pessoas;

import com.gupyclone.comum.EntidadeComId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/** Pessoa do RH. Ana no enunciado: cria vagas e define criterios de aceite. */
@Entity
@Table(name = "recrutador")
public class Recrutador extends EntidadeComId {

    @Column(name = "chave", nullable = false, updatable = false, length = 40)
    private String chave;

    @Column(name = "nome", nullable = false, length = 120)
    private String nome;

    @Column(name = "email", nullable = false, length = 180)
    private String email;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private OffsetDateTime criadoEm;

    protected Recrutador() {
    }

    public Recrutador(String chave, String nome, String email) {
        this.chave = chave;
        this.nome = nome;
        this.email = email;
    }

    @PrePersist
    void aoPersistir() {
        if (criadoEm == null) {
            criadoEm = OffsetDateTime.now();
        }
    }

    public String getChave() {
        return chave;
    }

    public String getNome() {
        return nome;
    }

    public String getEmail() {
        return email;
    }

    public OffsetDateTime getCriadoEm() {
        return criadoEm;
    }
}
