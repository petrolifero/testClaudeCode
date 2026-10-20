package com.gupyclone.seguranca;

import java.util.Optional;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Acesso ao usuario autenticado para servicos e controladores de pagina. */
@Component
public class UsuarioAtual {

    public Optional<UsuarioAutenticado> obter() {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacao == null || !autenticacao.isAuthenticated()) {
            return Optional.empty();
        }
        return autenticacao.getPrincipal() instanceof UsuarioAutenticado usuario
                ? Optional.of(usuario)
                : Optional.empty();
    }

    public UsuarioAutenticado exigir() {
        return obter().orElseThrow(() -> new AccessDeniedException("e preciso entrar como alguem"));
    }

    public UsuarioAutenticado exigirCandidato() {
        UsuarioAutenticado usuario = exigir();
        if (!usuario.ehCandidato()) {
            throw new AccessDeniedException("acao exclusiva de candidatos");
        }
        return usuario;
    }

    public UsuarioAutenticado exigirRh() {
        UsuarioAutenticado usuario = exigir();
        if (!usuario.ehRh()) {
            throw new AccessDeniedException("acao exclusiva do RH");
        }
        return usuario;
    }
}
