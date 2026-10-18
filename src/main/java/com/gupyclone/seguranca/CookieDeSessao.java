package com.gupyclone.seguranca;

import com.gupyclone.config.PropriedadesJwt;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Optional;
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

    /** Token de sessao presente na requisicao, se houver. */
    public Optional<String> tokenNaRequisicao(HttpServletRequest requisicao) {
        Cookie[] presentes = requisicao.getCookies();
        if (presentes == null) {
            return Optional.empty();
        }
        return Arrays.stream(presentes)
                .filter(cookie -> propriedades.cookie().equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(valor -> valor != null && !valor.isBlank())
                .findFirst();
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
