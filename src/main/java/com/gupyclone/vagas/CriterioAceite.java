package com.gupyclone.vagas;

import com.gupyclone.comum.EntidadeComId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Criterio de aceite de uma vaga, definido pelo RH.
 *
 * <p>{@code expressao} e o que o analisador textual joga em
 * {@code websearch_to_tsquery}: aceita {@code "java or jvm"}, {@code "-php"} e
 * frases entre aspas, e nunca estoura erro de sintaxe com texto livre.
 */
@Entity
@Table(name = "criterio_aceite")
public class CriterioAceite extends EntidadeComId {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vaga_id", nullable = false)
    private Vaga vaga;

    @Column(name = "descricao", nullable = false, length = 200)
    private String descricao;

    @Column(name = "expressao", nullable = false, length = 200)
    private String expressao;

    @Column(name = "peso", nullable = false)
    private int peso;

    @Column(name = "obrigatorio", nullable = false)
    private boolean obrigatorio;

    protected CriterioAceite() {
    }

    public CriterioAceite(String descricao, String expressao, int peso, boolean obrigatorio) {
        if (peso < 1 || peso > 100) {
            throw new IllegalArgumentException("peso do criterio deve estar entre 1 e 100");
        }
        this.descricao = descricao;
        this.expressao = expressao;
        this.peso = peso;
        this.obrigatorio = obrigatorio;
    }

    void associarA(Vaga vaga) {
        this.vaga = vaga;
    }

    public Vaga getVaga() {
        return vaga;
    }

    public String getDescricao() {
        return descricao;
    }

    public String getExpressao() {
        return expressao;
    }

    public int getPeso() {
        return peso;
    }

    public boolean isObrigatorio() {
        return obrigatorio;
    }
}
