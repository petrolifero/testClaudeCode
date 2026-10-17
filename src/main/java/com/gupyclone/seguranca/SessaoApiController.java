package com.gupyclone.seguranca;

import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Mesmo fluxo de entrar/sair, em JSON.
 *
 * <p>A API devolve o token para ser usado em {@code Authorization: Bearer} e
 * tambem manda o cookie, de modo que o navegador e os testes compartilham o
 * mesmo caminho de codigo.
 */
@RestController
public class SessaoApiController {

    /** Resposta de entrada: o token e quem ele representa. */
    public record RespostaDeEntrada(String token, String chave, String nome, Papel papel, Long entidadeId) {
    }

    /** Persona oferecida no cabecalho quando ninguem esta autenticado. */
    public record PersonaDisponivel(String chave, String nome, Papel papel) {
    }

    private final ServicoDePersonas personas;
    private final ServicoDeToken tokens;
    private final CookieDeSessao cookies;
    private final UsuarioAtual usuarioAtual;

    public SessaoApiController(ServicoDePersonas personas,
                               ServicoDeToken tokens,
                               CookieDeSessao cookies,
                               UsuarioAtual usuarioAtual) {
        this.personas = personas;
        this.tokens = tokens;
        this.cookies = cookies;
        this.usuarioAtual = usuarioAtual;
    }

    @GetMapping("/api/auth/personas")
    public List<PersonaDisponivel> disponiveis() {
        return personas.disponiveis().stream()
                .map(persona -> new PersonaDisponivel(persona.chave(), persona.nome(), persona.papel()))
                .toList();
    }

    @PostMapping("/api/auth/entrar/{chave}")
    public ResponseEntity<RespostaDeEntrada> entrar(@PathVariable String chave) {
        usuarioAtual.obter().ifPresent(usuario -> {
            throw new JaAutenticadoException(usuario.chave());
        });
        UsuarioAutenticado usuario = personas.resolver(chave);
        String token = tokens.emitir(usuario);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookies.comToken(token).toString())
                .body(new RespostaDeEntrada(token, usuario.chave(), usuario.nome(), usuario.papel(),
                        usuario.entidadeId()));
    }

    @PostMapping("/api/auth/sair")
    public ResponseEntity<Void> sair() {
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookies.expirado().toString())
                .build();
    }

    @GetMapping("/api/eu")
    public UsuarioAutenticado eu(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return usuario;
    }
}
