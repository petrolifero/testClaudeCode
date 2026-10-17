package com.gupyclone.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuracao do token de acesso emitido ao "entrar como" uma persona. */
@ConfigurationProperties(prefix = "gupy.jwt")
public record PropriedadesJwt(String segredo, Duration validade, String cookie, boolean cookieSeguro) {

    public PropriedadesJwt {
        if (segredo == null || segredo.getBytes().length < 32) {
            throw new IllegalArgumentException("gupy.jwt.segredo precisa de pelo menos 32 bytes para HS256");
        }
    }
}
