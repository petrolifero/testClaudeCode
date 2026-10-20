package com.gupyclone.web;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

/**
 * Um navegador de mentirinha, mas com HTTP de verdade.
 *
 * <p>Guarda o cookie de sessao entre requisicoes e reenvia o token CSRF que o
 * Thymeleaf injeta nos formularios. Existe porque o caminho que precisa ser
 * provado e justamente o do navegador: cookie HttpOnly + CSRF, e nao
 * {@code Authorization: Bearer}, que e o caminho da API.
 *
 * <p>Nao segue redirecionamento de proposito: varias afirmacoes sao sobre o
 * 302 e o Location.
 */
class NavegadorDeTeste {

    /** Uma resposta HTTP, com o HTML ja parseado quando for HTML. */
    record Resposta(int status, String corpo, String localizacao) {

        Document html() {
            return Jsoup.parse(corpo);
        }
    }

    private final String base;
    private final HttpClient cliente;
    private String tokenCsrf;

    NavegadorDeTeste(String base) {
        this.base = base;
        this.cliente = HttpClient.newBuilder()
                .cookieHandler(new CookieManager())
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    Resposta abrir(String caminho) {
        Resposta resposta = enviar(HttpRequest.newBuilder(URI.create(base + caminho)).GET().build());
        guardarCsrf(resposta);
        return resposta;
    }

    /** POST de formulario comum (application/x-www-form-urlencoded), com o CSRF da ultima pagina. */
    Resposta postar(String caminho, Map<String, String> campos) {
        return postar(caminho, new LinkedHashMap<>(campos).entrySet().stream().toList());
    }

    /**
     * Mesmo POST, mas aceitando a MESMA chave repetida: e assim que o
     * formulario de nova vaga envia as linhas de criterio (listas paralelas).
     */
    Resposta postar(String caminho, List<Map.Entry<String, String>> campos) {
        List<Map.Entry<String, String>> corpo = new ArrayList<>(campos);
        if (tokenCsrf != null) {
            corpo.add(Map.entry("_csrf", tokenCsrf));
        }
        String codificado = corpo.stream()
                .map(campo -> urlEncode(campo.getKey()) + "=" + urlEncode(campo.getValue()))
                .reduce((a, b) -> a + "&" + b)
                .orElse("");
        return enviar(HttpRequest.newBuilder(URI.create(base + caminho))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(codificado, StandardCharsets.UTF_8))
                .build());
    }

    Resposta postar(String caminho) {
        return postar(caminho, Map.of());
    }

    /** POST multipart com um arquivo, como o formulario de curriculo faz. */
    Resposta enviarArquivo(String caminho, String nomeDoCampo, String nomeDoArquivo, byte[] conteudo) {
        String fronteira = "----gupyclone" + nomeDoCampo.hashCode();
        ByteArrayOutputStream corpo = new ByteArrayOutputStream();
        try {
            if (tokenCsrf != null) {
                corpo.write(("--" + fronteira + "\r\n"
                        + "Content-Disposition: form-data; name=\"_csrf\"\r\n\r\n"
                        + tokenCsrf + "\r\n").getBytes(StandardCharsets.UTF_8));
            }
            corpo.write(("--" + fronteira + "\r\n"
                    + "Content-Disposition: form-data; name=\"" + nomeDoCampo
                    + "\"; filename=\"" + nomeDoArquivo + "\"\r\n"
                    + "Content-Type: application/pdf\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            corpo.write(conteudo);
            corpo.write(("\r\n--" + fronteira + "--\r\n").getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return enviar(HttpRequest.newBuilder(URI.create(base + caminho))
                .header("Content-Type", "multipart/form-data; boundary=" + fronteira)
                .POST(HttpRequest.BodyPublishers.ofByteArray(corpo.toByteArray()))
                .build());
    }

    /** Segue um redirecionamento relativo, como o navegador faria. */
    Resposta seguir(Resposta resposta) {
        String destino = resposta.localizacao();
        if (destino == null) {
            throw new IllegalStateException("resposta " + resposta.status() + " nao tem Location");
        }
        return abrir(destino.startsWith(base) ? destino.substring(base.length()) : destino);
    }

    private Resposta enviar(HttpRequest requisicao) {
        try {
            HttpResponse<String> resposta = cliente.send(requisicao, HttpResponse.BodyHandlers.ofString());
            return new Resposta(resposta.statusCode(), resposta.body(),
                    resposta.headers().firstValue("Location").orElse(null));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("requisicao interrompida", e);
        }
    }

    private void guardarCsrf(Resposta resposta) {
        var campo = resposta.html().selectFirst("input[name=_csrf]");
        if (campo != null) {
            tokenCsrf = campo.attr("value");
        }
    }

    private static String urlEncode(String valor) {
        return java.net.URLEncoder.encode(valor, StandardCharsets.UTF_8);
    }
}
