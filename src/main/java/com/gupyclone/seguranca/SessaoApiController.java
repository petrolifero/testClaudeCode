package com.gupyclone.seguranca;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Optional;
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

    /**
     * Entrar pela API.
     *
     * <p>Devolve o token no corpo e <strong>nao</strong> emite cookie: cookie e
     * do fluxo HTML. Emitir cookie aqui era perigoso porque esta rota e
     * publica e isenta de CSRF (a isencao se justifica justamente por a API nao
     * aceitar cookie), de modo que um formulario em outro site conseguia trocar
     * a persona do navegador da vitima.
     *
     * <p>A recusa de "ja autenticado" tambem olha o cookie, e nao apenas o
     * contexto de seguranca: como o filtro ignora cookie em {@code /api/**}, um
     * navegador logado chegava aqui como anonimo e trocava de pessoa sem sair,
     * contra o 409 que o contrato promete.
     */
    @PostMapping("/api/auth/entrar/{chave}")
    public ResponseEntity<RespostaDeEntrada> entrar(@PathVariable String chave,
                                                   HttpServletRequest requisicao) {
        sessaoJaExistente(requisicao).ifPresent(chaveAtual -> {
            throw new JaAutenticadoException(chaveAtual);
        });
        UsuarioAutenticado usuario = personas.resolver(chave);
        String token = tokens.emitir(usuario);
        return ResponseEntity.ok()
                .body(new RespostaDeEntrada(token, usuario.chave(), usuario.nome(), usuario.papel(),
                        usuario.entidadeId()));
    }

    /** Chave de quem ja esta autenticado, seja por Bearer, seja por cookie. */
    private Optional<String> sessaoJaExistente(HttpServletRequest requisicao) {
        Optional<String> doContexto = usuarioAtual.obter().map(UsuarioAutenticado::chave);
        if (doContexto.isPresent()) {
            return doContexto;
        }
        return cookies.tokenNaRequisicao(requisicao).flatMap(token -> {
            try {
                return Optional.of(tokens.ler(token).chave());
            } catch (TokenInvalidoException e) {
                // Cookie velho ou invalido nao e sessao: deixa entrar.
                return Optional.empty();
            }
        });
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
