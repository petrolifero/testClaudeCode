package com.gupyclone.seguranca;

import static org.assertj.core.api.Assertions.assertThat;

import com.gupyclone.config.PropriedadesJwt;
import com.gupyclone.suporte.TesteDeIntegracao;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Fixa as duas garantias que o README chama de "por construcao" e que, ate a
 * revisao adversarial, nenhum teste provava.
 *
 * <p>Nao usa o NavegadorDeTeste de proposito: ele anexa o token CSRF e o
 * cookie automaticamente, que e exatamente o que estes testes precisam
 * controlar na mao.
 */
class InvariantesDeSegurancaTest extends TesteDeIntegracao {

    @Autowired
    private PropriedadesJwt propriedadesJwt;

    private final HttpClient cliente = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    @Test
    @DisplayName("o cookie NAO autentica /api/**")
    void cookieNaoAutenticaApi() {
        var resposta = comCookie("/api/candidaturas/minhas", tokenDe("carlos"));

        assertThat(resposta.statusCode())
                .as("a API so aceita Authorization: Bearer")
                .isEqualTo(401);
    }

    @Test
    @DisplayName("o cookie tambem nao autentica /api/** com o caminho percent-encoded")
    void cookieNaoAutenticaApiComCaminhoCodificado() {
        // /%61pi/ decodifica para /api/. Enquanto o filtro e a cadeia de
        // seguranca comparavam getRequestURI() cru, o filtro NAO reconhecia
        // isto como API (e aceitava o cookie) e a autorizacao, que casa pelo
        // caminho decodificado, reconhecia: o cookie autenticava a API.
        var resposta = comCookie("/%61pi/candidaturas/minhas", tokenDe("carlos"));

        assertThat(resposta.statusCode())
                .as("as duas leituras do caminho tem de concordar")
                .isEqualTo(401);
        assertThat(resposta.body()).doesNotContain("vagaId");
    }

    @Test
    @DisplayName("Bearer continua autenticando a API normalmente")
    void bearerAutenticaApi() {
        var resposta = enviar(HttpRequest.newBuilder(URI.create(enderecoBase() + "/api/candidaturas/minhas"))
                .header("Authorization", "Bearer " + tokenDe("carlos"))
                .GET().build());

        assertThat(resposta.statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("POST de pagina sem token CSRF e recusado")
    void postDePaginaSemCsrfERecusado() {
        var resposta = enviar(HttpRequest.newBuilder(URI.create(enderecoBase() + "/auth/entrar/carlos"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("", StandardCharsets.UTF_8))
                .build());

        assertThat(resposta.statusCode())
                .as("a interface HTML depende do token CSRF; sem ele, 403")
                .isEqualTo(403);
    }

    @Test
    @DisplayName("entrar pela API com sessao por cookie devolve 409, como o contrato promete")
    void entrarPelaApiComCookieDevolve409() {
        var resposta = enviar(HttpRequest.newBuilder(URI.create(enderecoBase() + "/api/auth/entrar/joao"))
                .header("Cookie", propriedadesJwt.cookie() + "=" + tokenDe("carlos"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build());

        assertThat(resposta.statusCode())
                .as("trocar de pessoa sem sair antes e recusado, inclusive pela API")
                .isEqualTo(409);
        assertThat(resposta.body()).contains("ja autenticado");
    }

    @Test
    @DisplayName("entrar pela API com Bearer tambem devolve 409")
    void entrarPelaApiComBearerDevolve409() {
        var resposta = enviar(HttpRequest.newBuilder(URI.create(enderecoBase() + "/api/auth/entrar/joao"))
                .header("Authorization", "Bearer " + tokenDe("carlos"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build());

        assertThat(resposta.statusCode()).isEqualTo(409);
    }

    @Test
    @DisplayName("entrar pela API devolve o token no corpo e NAO emite cookie")
    void entrarPelaApiNaoEmiteCookie() {
        var resposta = enviar(HttpRequest.newBuilder(URI.create(enderecoBase() + "/api/auth/entrar/carlos"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build());

        assertThat(resposta.statusCode()).isEqualTo(200);
        assertThat(resposta.body()).contains("\"chave\":\"carlos\"").contains("\"token\"");
        assertThat(resposta.headers().firstValue("Set-Cookie"))
                .as("cookie e do fluxo HTML; emiti-lo aqui deixava outro site trocar a persona da vitima")
                .isEmpty();
    }

    @Test
    @DisplayName("token invalido no cookie nao impede entrar")
    void cookieInvalidoNaoImpedeEntrar() {
        var resposta = enviar(HttpRequest.newBuilder(URI.create(enderecoBase() + "/api/auth/entrar/carlos"))
                .header("Cookie", propriedadesJwt.cookie() + "=isto-nao-e-um-jwt")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build());

        assertThat(resposta.statusCode())
                .as("cookie velho ou corrompido nao e sessao")
                .isEqualTo(200);
    }

    @Test
    @DisplayName("token assinado com outro segredo nao autentica")
    void tokenDeOutroSegredoNaoAutentica() {
        // Mesmo payload, assinatura de outra chave: tem de ser recusado.
        String forjado = tokenDe("ana");
        String adulterado = forjado.substring(0, forjado.lastIndexOf('.') + 1) + "YXNzaW5hdHVyYS1mYWxzYQ";

        var resposta = enviar(HttpRequest.newBuilder(URI.create(enderecoBase() + "/api/eu"))
                .header("Authorization", "Bearer " + adulterado)
                .GET().build());

        assertThat(resposta.statusCode()).isEqualTo(401);
    }

    private HttpResponse<String> comCookie(String caminho, String token) {
        return enviar(HttpRequest.newBuilder(URI.create(enderecoBase() + caminho))
                .header("Cookie", propriedadesJwt.cookie() + "=" + token)
                .GET().build());
    }

    private HttpResponse<String> enviar(HttpRequest requisicao) {
        try {
            return cliente.send(requisicao, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("requisicao interrompida", e);
        }
    }
}
