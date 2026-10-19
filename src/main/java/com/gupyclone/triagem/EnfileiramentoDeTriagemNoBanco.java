package com.gupyclone.triagem;

import com.gupyclone.candidaturas.EnfileiramentoDeTriagem;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fila de analise como tabela do proprio banco (padrao outbox).
 *
 * <p>Esta classe e o lado da triagem da porta que candidaturas declara: e por
 * ela que o modulo de candidaturas enfileira uma analise sem conhecer
 * {@link AnaliseJob} nem o repositorio dele.
 */
@Service
public class EnfileiramentoDeTriagemNoBanco implements EnfileiramentoDeTriagem {

    private final AnaliseJobRepository analises;

    public EnfileiramentoDeTriagemNoBanco(AnaliseJobRepository analises) {
        this.analises = analises;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Exige a transacao de quem grava a candidatura: ver a escolha de
     * propagacao no codigo.
     */
    // MANDATORY, como na fila de e-mails e pela mesma razao: "se a candidatura
    // existe, o job existe" so vale se os dois forem gravados na mesma
    // transacao. REQUIRES_NEW comitaria o job sozinho e um rollback do chamador
    // deixaria analise para uma candidatura que nao aconteceu; REQUIRED
    // aceitaria calado um chamador sem transacao, que e exatamente o erro que
    // desfaz a garantia.
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void enfileirar(Long candidaturaId) {
        // saveAndFlush, e nao save: com save o insert seria adiado para o
        // commit, e a colisao no unique (candidatura_id) chegaria a quem chamou
        // como falha de commit em vez de DataIntegrityViolationException.
        analises.saveAndFlush(new AnaliseJob(candidaturaId));
    }
}
