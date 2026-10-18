package com.gupyclone.config;

import java.time.Duration;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuracao do token de acesso emitido ao "entrar como" uma persona. */
@ConfigurationProperties(prefix = "gupy.jwt")
public record PropriedadesJwt(String segredo, Duration validade, String cookie, boolean cookieSeguro) {

    private static final Logger log = LoggerFactory.getLogger(PropriedadesJwt.class);

    /** Valores que acompanham o repositorio e nunca devem assinar token real. */
    private static final Set<String> SEGREDOS_DE_DESENVOLVIMENTO = Set.of(
            "segredo-de-desenvolvimento-com-32-bytes-ou-mais",
            "troque-este-segredo-em-producao-com-32-bytes",
            "segredo-exclusivo-de-teste-com-mais-de-32-bytes");

    public PropriedadesJwt {
        if (segredo == null || segredo.getBytes().length < 32) {
            throw new IllegalArgumentException("gupy.jwt.segredo precisa de pelo menos 32 bytes para HS256");
        }
        if (SEGREDOS_DE_DESENVOLVIMENTO.contains(segredo)) {
            // Aviso, nao falha: derrubar a subida impediria rodar o projeto
            // recem-clonado, que e o caminho que o README ensina. Mas quem
            // sobe isso em producao tem de ver no log.
            log.warn("gupy.jwt.segredo esta com um valor de desenvolvimento conhecido. "
                    + "Defina GUPY_JWT_SEGREDO antes de expor o sistema.");
        }
    }

    public boolean ehSegredoDeDesenvolvimento() {
        return SEGREDOS_DE_DESENVOLVIMENTO.contains(segredo);
    }
}
