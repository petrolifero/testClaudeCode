package com.gupyclone.seguranca;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Entrar e sair pela interface HTML.
 *
 * <p>Entrar como alguem e um POST (muda estado e leva CSRF token do Thymeleaf),
 * nunca um GET.
 */
@Controller
@RequestMapping("/auth")
public class AutenticacaoController {

    private final ServicoDePersonas personas;
    private final ServicoDeToken tokens;
    private final CookieDeSessao cookies;
    private final UsuarioAtual usuarioAtual;

    public AutenticacaoController(ServicoDePersonas personas,
                                  ServicoDeToken tokens,
                                  CookieDeSessao cookies,
                                  UsuarioAtual usuarioAtual) {
        this.personas = personas;
        this.tokens = tokens;
        this.cookies = cookies;
        this.usuarioAtual = usuarioAtual;
    }

    @PostMapping("/entrar/{chave}")
    public String entrar(@PathVariable String chave, HttpServletResponse resposta) {
        // Ja logado? Nao troca de pessoa sem sair antes.
        var jaAutenticado = usuarioAtual.obter();
        if (jaAutenticado.isPresent()) {
            return "redirect:/?aviso=saia-antes-de-entrar-como-outra-pessoa";
        }
        UsuarioAutenticado usuario = personas.resolver(chave);
        resposta.addHeader(HttpHeaders.SET_COOKIE, cookies.comToken(tokens.emitir(usuario)).toString());
        return "redirect:/vagas";
    }

    @PostMapping("/sair")
    public String sair(HttpServletResponse resposta) {
        resposta.addHeader(HttpHeaders.SET_COOKIE, cookies.expirado().toString());
        return "redirect:/";
    }
}
