package com.gupyclone.config;

import com.gupyclone.seguranca.FiltroDeAutenticacaoJwt;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Autorizacao por papel. Sem sessao e sem login: quem autentica e o JWT.
 *
 * <p>Deslogado so existe a pagina inicial (e os recursos estaticos e os
 * endpoints de entrar/sair). Qualquer outra pagina exige estar autenticado.
 *
 * <p>Sobre CSRF: a interface HTML autentica por cookie e portanto mantem o
 * token CSRF (o Thymeleaf injeta nos formularios); a API {@code /api/**} so
 * aceita {@code Authorization: Bearer} — ver {@link FiltroDeAutenticacaoJwt} —
 * e por isso dispensa CSRF sem abrir brecha.
 */
@Configuration
@EnableMethodSecurity
public class SegurancaConfig {

    /** Casa com /api/** sem depender de classes de matcher que mudam entre versoes. */
    private static final RequestMatcher API = (HttpServletRequest requisicao) -> {
        String caminho = requisicao.getRequestURI();
        String contexto = requisicao.getContextPath();
        if (contexto != null && !contexto.isEmpty() && caminho.startsWith(contexto)) {
            caminho = caminho.substring(contexto.length());
        }
        return caminho.startsWith("/api/");
    };

    /** Catch-all: usado como ultimo mapeamento de entry point. */
    private static final RequestMatcher QUALQUER_REQUISICAO = requisicao -> true;

    @Bean
    SecurityFilterChain cadeiaDeFiltros(HttpSecurity http, FiltroDeAutenticacaoJwt filtroJwt) throws Exception {
        http
                .csrf(csrf -> csrf.ignoringRequestMatchers(API))
                .sessionManagement(sessao -> sessao.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(autorizacao -> autorizacao
                        .requestMatchers("/", "/error", "/favicon.ico", "/css/**", "/js/**").permitAll()
                        .requestMatchers("/auth/entrar/**", "/auth/sair").permitAll()
                        .requestMatchers("/api/auth/**").permitAll()
                        // RH: publica vagas e acompanha as candidaturas das suas vagas.
                        .requestMatchers(HttpMethod.POST, "/api/vagas").hasRole("RH")
                        .requestMatchers(HttpMethod.GET, "/api/vagas/*/candidaturas").hasRole("RH")
                        .requestMatchers("/rh/**").hasRole("RH")
                        // Candidato: se candidata e acompanha as proprias candidaturas.
                        .requestMatchers(HttpMethod.POST, "/api/vagas/*/candidaturas").hasRole("CANDIDATO")
                        .requestMatchers("/api/candidaturas/minhas").hasRole("CANDIDATO")
                        .requestMatchers("/minhas-candidaturas").hasRole("CANDIDATO")
                        .requestMatchers(HttpMethod.POST, "/vagas/*/candidatar").hasRole("CANDIDATO")
                        .anyRequest().authenticated())
                .exceptionHandling(excecoes -> excecoes
                        // A ordem importa: o primeiro matcher que casar decide.
                        // API responde 401; o navegador volta para a pagina inicial.
                        .defaultAuthenticationEntryPointFor(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED), API)
                        .defaultAuthenticationEntryPointFor(
                                (requisicao, resposta, erro) -> resposta.sendRedirect(
                                        requisicao.getContextPath() + "/?aviso=entre-para-continuar"),
                                QUALQUER_REQUISICAO))
                .addFilterBefore(filtroJwt, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
