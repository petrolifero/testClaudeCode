package com.gupyclone.seguranca;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Autentica a requisicao a partir do JWT.
 *
 * <p>Modelo de seguranca, deliberado:
 * <ul>
 *   <li>A interface HTML usa <strong>cookie</strong> HttpOnly + SameSite=Strict,
 *       e e protegida por CSRF token (o Thymeleaf injeta nos formularios).</li>
 *   <li>A API {@code /api/**} aceita <strong>somente</strong>
 *       {@code Authorization: Bearer}. Por construcao nao ha o que falsificar
 *       via navegador de terceiros, e por isso o CSRF pode ser dispensado la.</li>
 * </ul>
 * Ou seja: o cookie nunca autentica {@code /api/**}.
 */
@Component
public class FiltroDeAutenticacaoJwt extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(FiltroDeAutenticacaoJwt.class);

    private final ServicoDeToken servicoDeToken;
    private final CookieDeSessao cookies;

    public FiltroDeAutenticacaoJwt(ServicoDeToken servicoDeToken, CookieDeSessao cookies) {
        this.servicoDeToken = servicoDeToken;
        this.cookies = cookies;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest requisicao,
                                    HttpServletResponse resposta,
                                    FilterChain cadeia) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            extrairToken(requisicao).ifPresent(token -> autenticar(token, requisicao));
        }
        cadeia.doFilter(requisicao, resposta);
    }

    private Optional<String> extrairToken(HttpServletRequest requisicao) {
        String cabecalho = requisicao.getHeader("Authorization");
        if (cabecalho != null && cabecalho.startsWith("Bearer ")) {
            return Optional.of(cabecalho.substring("Bearer ".length()).trim());
        }
        if (ehRequisicaoDeApi(requisicao)) {
            return Optional.empty();
        }
        return cookieDeSessao(requisicao);
    }

    private boolean ehRequisicaoDeApi(HttpServletRequest requisicao) {
        return RequisicoesDaApi.ehApi(requisicao);
    }

    private Optional<String> cookieDeSessao(HttpServletRequest requisicao) {
        return cookies.tokenNaRequisicao(requisicao);
    }

    private void autenticar(String token, HttpServletRequest requisicao) {
        try {
            UsuarioAutenticado usuario = servicoDeToken.ler(token);
            var autenticacao = new UsernamePasswordAuthenticationToken(
                    usuario, null, List.of(new SimpleGrantedAuthority(usuario.papel().authority())));
            autenticacao.setDetails(requisicao.getRequestURI());
            SecurityContextHolder.getContext().setAuthentication(autenticacao);
        } catch (TokenInvalidoException e) {
            // Token ruim nao e erro: segue como anonimo e a autorizacao decide.
            log.debug("token ignorado: {}", e.getMessage());
            SecurityContextHolder.clearContext();
        }
    }
}
