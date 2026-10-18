package com.gupyclone.seguranca;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * O que conta como requisicao de API.
 *
 * <p>Existe como ponto unico porque a invariante "o cookie nunca autentica
 * {@code /api/**}" so vale se o filtro de autenticacao e a cadeia de seguranca
 * concordarem sobre o que e {@code /api/**}. Enquanto cada lado comparava
 * {@code getRequestURI()} cru, {@code /%61pi/candidaturas/minhas} passava: o
 * filtro nao reconhecia como API (e portanto aceitava o cookie) e a regra de
 * autorizacao, que casa pelo caminho decodificado, reconhecia. Resultado: o
 * cookie autenticava a API.
 *
 * <p>{@link PathPatternRequestMatcher} casa sobre o caminho ja parseado e
 * normalizado pelo Spring, de modo que percent-encoding e segmentos redundantes
 * nao criam duas leituras do mesmo caminho.
 */
public final class RequisicoesDaApi {

    public static final RequestMatcher MATCHER =
            PathPatternRequestMatcher.withDefaults().matcher("/api/**");

    private RequisicoesDaApi() {
    }

    public static boolean ehApi(HttpServletRequest requisicao) {
        return MATCHER.matches(requisicao);
    }
}
