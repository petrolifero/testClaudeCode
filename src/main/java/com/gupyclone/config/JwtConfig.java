package com.gupyclone.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/** Assinatura e verificacao simetrica (HS256) do token de acesso. */
@Configuration
public class JwtConfig {

    @Bean
    JwtEncoder jwtEncoder(PropriedadesJwt propriedades) {
        return new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(chave(propriedades)));
    }

    @Bean
    JwtDecoder jwtDecoder(PropriedadesJwt propriedades) {
        return NimbusJwtDecoder.withSecretKey(chave(propriedades))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    private static SecretKey chave(PropriedadesJwt propriedades) {
        return new SecretKeySpec(propriedades.segredo().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }
}
