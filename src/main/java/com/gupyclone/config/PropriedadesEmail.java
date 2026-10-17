package com.gupyclone.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuracao do despacho de e-mails da fila (email_outbox). */
@ConfigurationProperties(prefix = "gupy.email")
public record PropriedadesEmail(
        String remetente,
        Duration intervalo,
        int lote,
        int maxTentativas,
        Duration esperaAposFalha) {
}
