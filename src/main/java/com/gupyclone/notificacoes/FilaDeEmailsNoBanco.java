package com.gupyclone.notificacoes;

import com.gupyclone.candidaturas.Candidatura;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fila de e-mails como tabela do proprio banco (padrao outbox).
 *
 * <p>Nao envia nada: quem entrega e {@link DespachanteDeEmails}, depois, fora
 * de transacao. Aqui o trabalho e so gravar o aviso junto com o fato que o
 * originou.
 */
@Service
public class FilaDeEmailsNoBanco implements FilaDeEmails {

    private static final Logger log = LoggerFactory.getLogger(FilaDeEmailsNoBanco.class);

    private final EmailOutboxRepository emails;
    private final RedatorDeEmail redator;

    public FilaDeEmailsNoBanco(EmailOutboxRepository emails, RedatorDeEmail redator) {
        this.emails = emails;
        this.redator = redator;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Exige transacao de quem chamou: ver a escolha de propagacao no codigo.
     */
    // MANDATORY, e nao REQUIRED nem REQUIRES_NEW. O aviso so pode existir se a
    // candidatura tiver mesmo sido concluida, logo ele tem que ser gravado na
    // transacao de quem a concluiu. REQUIRES_NEW comitaria o aviso sozinho e um
    // rollback do chamador deixaria no banco um e-mail sobre algo que nao
    // aconteceu; REQUIRED aceitaria calado um chamador sem transacao, que e
    // exatamente o erro que desfaz a garantia. MANDATORY recusa esse chamador
    // na primeira chamada, em desenvolvimento, em vez de perder um aviso em
    // producao.
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void enfileirar(Candidatura candidatura, TipoEmail tipo, int pontuacao, String motivo) {
        if (candidatura == null || candidatura.getId() == null) {
            throw new IllegalArgumentException("so da para avisar sobre candidatura ja gravada");
        }
        if (emails.findByCandidaturaIdAndTipo(candidatura.getId(), tipo).isPresent()) {
            log.debug("aviso {} da candidatura {} ja estava na fila", tipo, candidatura.getId());
            return;
        }
        try {
            // saveAndFlush, e nao save: com save o Hibernate adiaria o insert
            // para o commit, fora deste try, e o catch abaixo nunca veria nada.
            emails.saveAndFlush(redator.redigir(candidatura, tipo, pontuacao, motivo));
        } catch (DataIntegrityViolationException colisao) {
            // Rede de seguranca para a janela entre a consulta e o insert. E a
            // consulta acima que faz do segundo enfileiramento um silencio: o
            // PostgreSQL aborta a transacao inteira ao violar o unique, entao
            // engolir aqui nao salvaria o commit do chamador. Perder essa
            // transacao e inofensivo — a conclusao da candidatura e idempotente
            // e a proxima passada refaz tudo.
            log.debug("aviso {} da candidatura {} foi enfileirado em paralelo",
                    tipo, candidatura.getId(), colisao);
        }
    }
}
