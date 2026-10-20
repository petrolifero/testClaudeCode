package com.gupyclone.triagem;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Varredura periodica da fila de analise.
 *
 * <p>Separado do processador para que os testes possam processar a fila na hora
 * que escolherem: com {@code gupy.agendamento.ativo=false} este bean nem
 * existe, e nenhuma asserticao disputa com o agendador.
 */
@Component
@ConditionalOnProperty(name = "gupy.agendamento.ativo", havingValue = "true", matchIfMissing = true)
public class AgendadorDeTriagem {

    private final ProcessadorDeAnalises processador;

    public AgendadorDeTriagem(ProcessadorDeAnalises processador) {
        this.processador = processador;
    }

    /**
     * Recupera antes de reivindicar: o que um worker morto deixou preso volta
     * para a fila e ja sai nesta mesma passada.
     *
     * <p>{@code fixedDelay}, nao {@code fixedRate}: se um lote demorar mais que
     * o intervalo, a proxima passada espera em vez de empilhar varredura sobre
     * varredura.
     */
    @Scheduled(fixedDelayString = "${gupy.triagem.intervalo}")
    void varrer() {
        // A quarentena vem antes de tudo: e o que impede que FALHO seja
        // terminal. Uma indisponibilidade curta esgota as tentativas; passada a
        // quarentena, o item volta para a fila em vez de ficar esquecido.
        processador.reenfileirarOsQueFalharam();
        processador.recuperarLeasesExpirados();
        processador.processarUmLote();
    }
}
