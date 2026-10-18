package com.gupyclone.notificacoes;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Varredura periodica da fila de e-mails.
 *
 * <p>Separado do despachante para que os testes possam processar a fila na hora
 * que escolherem: com {@code gupy.agendamento.ativo=false} este bean nem
 * existe, e nenhuma asserticao disputa com o agendador.
 */
@Component
@ConditionalOnProperty(name = "gupy.agendamento.ativo", havingValue = "true", matchIfMissing = true)
public class AgendadorDeEmails {

    private final DespachanteDeEmails despachante;

    public AgendadorDeEmails(DespachanteDeEmails despachante) {
        this.despachante = despachante;
    }

    /**
     * Recupera antes de reivindicar: o que um worker morto deixou preso volta
     * para a fila e ja sai nesta mesma passada.
     *
     * <p>{@code fixedDelay}, nao {@code fixedRate}: se um lote demorar mais que
     * o intervalo, a proxima passada espera em vez de empilhar varredura sobre
     * varredura.
     */
    @Scheduled(fixedDelayString = "${gupy.email.intervalo}")
    void varrer() {
        // A quarentena vem antes de tudo: e o que impede que FALHO seja
        // terminal. Uma indisponibilidade curta esgota as tentativas; passada a
        // quarentena, o item volta para a fila em vez de ficar esquecido.
        despachante.reenfileirarOsQueFalharam();
        despachante.recuperarLeasesExpirados();
        despachante.despacharUmLote();
    }
}
