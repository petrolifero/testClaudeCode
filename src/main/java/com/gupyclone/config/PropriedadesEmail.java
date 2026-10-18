package com.gupyclone.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuracao do despacho de e-mails da fila (email_outbox).
 *
 * @param lease depois disso um aviso reivindicado e considerado abandonado e
 *              volta para a fila. Folgado em relacao a um envio: devolver cedo
 *              demais custa um reenvio, devolver tarde custa um atraso.
 * @param quarentena depois disso um aviso FALHO volta para a fila, para que uma
 *                   queda do servidor SMTP nao engula o aviso em definitivo
 */
@ConfigurationProperties(prefix = "gupy.email")
public record PropriedadesEmail(
        String remetente,
        Duration intervalo,
        int lote,
        int maxTentativas,
        Duration esperaAposFalha,
        Duration lease,
        Duration quarentena) {

    public PropriedadesEmail {
        if (quarentena == null || quarentena.isZero() || quarentena.isNegative()) {
            quarentena = Duration.ofHours(1);
        }
    }
}
