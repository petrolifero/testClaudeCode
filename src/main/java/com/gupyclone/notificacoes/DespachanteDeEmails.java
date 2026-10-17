package com.gupyclone.notificacoes;

import com.gupyclone.config.PropriedadesEmail;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.util.List;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Tira avisos da fila e os entrega ao servidor SMTP.
 *
 * <p>A classe nao tem {@code @Transactional} de proposito. Um despacho e uma
 * sequencia de tres passos com naturezas diferentes:
 *
 * <ol>
 *   <li>reivindicar, em transacao curta e propria (lease no banco);</li>
 *   <li>entregar por SMTP, <em>fora</em> de qualquer transacao — segurar uma
 *       conexao do pool enquanto se conversa com um servidor de e-mail e o jeito
 *       mais rapido de esgotar o pool;</li>
 *   <li>concluir, em outra transacao curta.</li>
 * </ol>
 *
 * <p>Com isso a entrega e <em>at-least-once</em>: um processo que morra entre o
 * {@code send} e a conclusao faz o aviso voltar pela expiracao do lease e ser
 * reenviado. E o limite honesto de SMTP, e esta registrado no ARQUITETURA.md.
 */
@Service
public class DespachanteDeEmails {


    private static final String CHARSET = "UTF-8";

    /** O suficiente para diagnosticar sem transformar a coluna num log. */
    private static final int MAXIMO_DO_ERRO = 1000;

    private static final Logger log = LoggerFactory.getLogger(DespachanteDeEmails.class);

    private final EmailOutboxRepository emails;
    private final ReivindicadorDeEmails reivindicador;
    private final JavaMailSender correio;
    private final PropriedadesEmail propriedades;
    private final TransactionTemplate conclusao;

    public DespachanteDeEmails(EmailOutboxRepository emails,
                               ReivindicadorDeEmails reivindicador,
                               JavaMailSender correio,
                               PropriedadesEmail propriedades,
                               PlatformTransactionManager gerenciadorDeTransacoes) {
        this.emails = emails;
        this.reivindicador = reivindicador;
        this.correio = correio;
        this.propriedades = propriedades;
        this.conclusao = new TransactionTemplate(gerenciadorDeTransacoes);
        // REQUIRES_NEW pela mesma razao do reivindicador: a conclusao de cada
        // aviso e curta e independente, mesmo que alguem chame o despachante de
        // dentro de uma transacao.
        this.conclusao.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Reivindica um lote e entrega cada aviso dele.
     *
     * @return quantos avisos foram aceitos pelo servidor SMTP
     */
    public int despacharUmLote() {
        List<Long> reivindicados = reivindicador.reivindicar(propriedades.lote());
        int enviados = 0;
        for (Long id : reivindicados) {
            if (despachar(id)) {
                enviados++;
            }
        }
        return enviados;
    }

    /**
     * Devolve a fila os avisos cujo worker morreu depois de reivindicar.
     *
     * @return quantos voltaram para PENDENTE
     */
    public int recuperarLeasesExpirados() {
        return reivindicador.devolverLeasesExpirados(propriedades.lease());
    }

    private boolean despachar(Long id) {
        EmailOutbox aviso = emails.findById(id).orElse(null);
        if (aviso == null) {
            // A candidatura foi apagada entre a reivindicacao e agora, e o
            // aviso foi junto por cascade: nao ha o que enviar nem o que
            // devolver para a fila.
            log.debug("aviso {} desapareceu depois de reivindicado", id);
            return false;
        }
        try {
            entregar(aviso);
        } catch (MailException | MessagingException falha) {
            log.warn("falha ao enviar o aviso {} para {}: {}", id, aviso.getDestinatario(), falha.getMessage());
            concluir(id, linha -> linha.registrarFalha(descrever(falha),
                    propriedades.maxTentativas(),
                    propriedades.esperaAposFalha()));
            return false;
        }
        concluir(id, EmailOutbox::marcarEnviado);
        return true;
    }

    private void entregar(EmailOutbox aviso) throws MessagingException {
        MimeMessage mensagem = correio.createMimeMessage();
        // O charset no helper e o que faz assunto (encoded-word) e corpo
        // (text/plain; charset=UTF-8) chegarem com a acentuacao inteira.
        MimeMessageHelper ajudante = new MimeMessageHelper(mensagem, false, CHARSET);
        ajudante.setFrom(propriedades.remetente());
        ajudante.setTo(aviso.getDestinatario());
        ajudante.setSubject(aviso.getAssunto());
        ajudante.setText(aviso.getCorpo(), false);
        correio.send(mensagem);
    }

    private void concluir(Long id, Consumer<EmailOutbox> desfecho) {
        conclusao.executeWithoutResult(status -> emails.findById(id).ifPresent(desfecho));
    }

    private static String descrever(Exception falha) {
        String descricao = falha.getClass().getSimpleName()
                + (falha.getMessage() == null ? "" : ": " + falha.getMessage());
        return descricao.length() <= MAXIMO_DO_ERRO ? descricao : descricao.substring(0, MAXIMO_DO_ERRO);
    }
}
