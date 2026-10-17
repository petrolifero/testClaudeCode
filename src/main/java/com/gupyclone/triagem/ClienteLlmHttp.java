package com.gupyclone.triagem;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.gupyclone.config.PropriedadesLlm;
import java.net.http.HttpClient;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Cliente da API de mensagens no formato Anthropic.
 *
 * <p>O bean nasce <strong>sem</strong> exigir credencial: a chave e vazia por
 * default em {@code application.yml} e so a chamada falha, nunca o boot. Quem
 * roda com a estrategia textual nao precisa de chave de LLM para subir a
 * aplicacao, e quem roda com a estrategia "llm" descobre o problema no primeiro
 * job (que volta para a fila) em vez de num contexto que nao sobe.
 *
 * <p>Timeout de conexao e de leitura vem das propriedades: sem eles, uma LLM
 * que aceita a conexao e nao responde prende o worker de triagem ate o timeout
 * do sistema operacional.
 */
@Component
public class ClienteLlmHttp implements ClienteLlm {

    private static final String ROTA_DE_MENSAGENS = "/v1/messages";

    /** Versao do contrato da API, exigida em todo pedido. */
    private static final String VERSAO_DA_API = "2023-06-01";

    /** Teto generoso para um veredito em JSON, e barato o bastante por job. */
    private static final int MAXIMO_DE_TOKENS = 1024;

    private final RestClient http;
    private final PropriedadesLlm propriedades;

    public ClienteLlmHttp(PropriedadesLlm propriedades) {
        this.propriedades = propriedades;
        HttpClient cliente = HttpClient.newBuilder().connectTimeout(propriedades.timeout()).build();
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(cliente);
        fabrica.setReadTimeout(propriedades.timeout());
        this.http = RestClient.builder()
                .baseUrl(propriedades.baseUrl())
                .requestFactory(fabrica)
                .build();
    }

    @Override
    public String perguntar(String instrucao, String pergunta) {
        Resposta resposta;
        try {
            resposta = http.post()
                    .uri(ROTA_DE_MENSAGENS)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("anthropic-version", VERSAO_DA_API)
                    .headers(cabecalhos -> {
                        // Sem chave, nenhum cabecalho: melhor um 401 claro do
                        // servidor do que um "x-api-key: " vazio no caminho.
                        if (propriedades.apiKey() != null && !propriedades.apiKey().isBlank()) {
                            cabecalhos.set("x-api-key", propriedades.apiKey());
                        }
                    })
                    .body(new Pedido(propriedades.modelo(),
                            MAXIMO_DE_TOKENS,
                            instrucao,
                            List.of(new Mensagem("user", pergunta))))
                    .retrieve()
                    .body(Resposta.class);
        } catch (RestClientException falha) {
            throw new AnaliseIndisponivelException(
                    "a LLM em " + propriedades.baseUrl() + " nao respondeu: " + falha.getMessage(), falha);
        }
        return textoDe(resposta);
    }

    private String textoDe(Resposta resposta) {
        if (resposta == null || resposta.content() == null) {
            throw new AnaliseIndisponivelException("a LLM em " + propriedades.baseUrl() + " respondeu sem corpo");
        }
        return resposta.content().stream()
                .filter(bloco -> "text".equals(bloco.type()) && bloco.text() != null && !bloco.text().isBlank())
                .map(Bloco::text)
                .findFirst()
                .orElseThrow(() -> new AnaliseIndisponivelException(
                        "a LLM em " + propriedades.baseUrl() + " respondeu sem nenhum bloco de texto"));
    }

    // Os tres records abaixo sao o formato do fio, nao do dominio: por isso os
    // nomes em ingles, iguais aos da API. O dominio ja esta em ResultadoDaAnalise.

    private record Pedido(String model,
                          @JsonProperty("max_tokens") int maxTokens,
                          String system,
                          List<Mensagem> messages) {
    }

    private record Mensagem(String role, String content) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Resposta(List<Bloco> content) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Bloco(String type, String text) {
    }
}
