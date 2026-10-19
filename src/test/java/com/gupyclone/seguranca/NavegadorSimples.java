package com.gupyclone.seguranca;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.jsoup.Jsoup;

/**
 * Navegador minimo para os testes de seguranca: guarda cookie e reenvia o token
 * CSRF da ultima pagina.
 *
 * <p>Separado do NavegadorDeTeste do pacote web de proposito: aqui varios
 * testes precisam enviar requisicao SEM cookie ou SEM CSRF, e um auxiliar que
 * anexa os dois automaticamente esconderia exatamente o que se quer provar.
 * Este so anexa quando o teste pede.
 */
class NavegadorSimples {

    private final String base;
    private final HttpClient cliente = HttpClient.newBuilder()
            .cookieHandler(new CookieManager())
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    private String csrf;

    NavegadorSimples(String base) {
        this.base = base;
    }

    void abrir(String caminho) {
        HttpResponse<String> resposta = enviar(HttpRequest.newBuilder(URI.create(base + caminho)).GET().build());
        var campo = Jsoup.parse(resposta.body()).selectFirst("input[name=_csrf]");
        if (campo != null) {
            csrf = campo.attr("value");
        }
    }

    HttpResponse<String> postarComCsrf(String caminho) {
        String corpo = csrf == null ? "" : "_csrf=" + csrf;
        return enviar(HttpRequest.newBuilder(URI.create(base + caminho))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(corpo, StandardCharsets.UTF_8))
                .build());
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
