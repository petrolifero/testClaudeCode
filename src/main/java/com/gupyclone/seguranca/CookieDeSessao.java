package com.gupyclone.seguranca;

import com.gupyclone.config.PropriedadesJwt;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Monta o cookie que mantem a persona "logada" no navegador.
 *
 * <p>HttpOnly para o token nao ser legivel por script e SameSite=Strict para o
 * navegador nao envia-lo em requisicoes partidas de outro site.
 */
@Component
public class CookieDeSessao {

    private final PropriedadesJwt propriedades;

    public CookieDeSessao(PropriedadesJwt propriedades) {
        this.propriedades = propriedades;
    }

    public ResponseCookie comToken(String token) {
        return ResponseCookie.from(propriedades.cookie(), token)
                .httpOnly(true)
                .secure(propriedades.cookieSeguro())
                .sameSite("Strict")
                .path("/")
                .maxAge(propriedades.validade())
                .build();
    }

    public ResponseCookie expirado() {
        return ResponseCookie.from(propriedades.cookie(), "")
                .httpOnly(true)
                .secure(propriedades.cookieSeguro())
                .sameSite("Strict")
                .path("/")
                .maxAge(0)
                .build();
    }
}
