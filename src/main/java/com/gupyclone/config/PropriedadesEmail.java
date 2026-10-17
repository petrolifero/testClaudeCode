package com.gupyclone.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuracao do despacho de e-mails da fila (email_outbox).
 *
 * @param lease depois disso um aviso reivindicado e considerado abandonado e
 *              volta para a fila. Folgado em relacao a um envio: devolver cedo
 *              demais custa um reenvio, devolver tarde custa um atraso.
 */
@ConfigurationProperties(prefix = "gupy.email")
public record PropriedadesEmail(
        String remetente,
        Duration intervalo,
        int lote,
        int maxTentativas,
        Duration esperaAposFalha,
        Duration lease) {
}
