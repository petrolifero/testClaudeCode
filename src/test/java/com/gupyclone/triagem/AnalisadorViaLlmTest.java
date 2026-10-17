package com.gupyclone.triagem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gupyclone.config.PropriedadesLlm;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * Prova da estrategia por LLM contra um servidor HTTP de verdade.
 *
 * <p>Nao e um mock: o {@link HttpServer} do JDK sobe numa porta livre, fala
 * HTTP de verdade, exige o cabecalho {@code x-api-key} como a API da Anthropic
 * exige e responde no formato dela. O que se prova aqui e o caminho inteiro —
 * serializacao do pedido, cabecalhos, parse da resposta e traducao em
 * {@link ResultadoDaAnalise} — e tambem os tres jeitos de isso dar errado:
 * resposta fora do formato, erro HTTP e servidor fora do ar.
 */
class AnalisadorViaLlmTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static final String CHAVE = "chave-de-teste";
    private static final String MODELO = "modelo-de-teste";

    private static final ContextoDaAnalise CONTEXTO = new ContextoDaAnalise(
            1L,
            2L,
            "Pessoa Desenvolvedora Java",
            "Trabalhar em serviços com Spring Boot e PostgreSQL",
            60,
            3L,
            "Carlos Pereira. Java, Spring Boot e PostgreSQL. Inglês fluente.",
            List.of(new ContextoDaAnalise.Criterio(10L, "Java", "java", 50, true),
                    new ContextoDaAnalise.Criterio(20L, "Inglês", "ingles", 50, false)));

    /** O que o servidor recebeu, para afirmar sobre o formato do pedido. */
    private record Recebido(String metodo, String caminho, String chave, String versao, String corpo) {
    }

    private final List<Recebido> recebidos = new ArrayList<>();

    private HttpServer servidor;
    private int status = 200;
    private String corpoDaResposta = envelope(vereditoEmJson(true, 88, "Critérios atendidos: Java, Inglês."));

    @BeforeEach
    void subirServidor() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        servidor.createContext("/v1/messages", this::responder);
        servidor.start();
    }

    @AfterEach
    void pararServidor() {
        servidor.stop(0);
    }

    @Test
    @DisplayName("o nome e o que a propriedade gupy.triagem.estrategia espera")
    void nomeDaEstrategia() {
        assertThat(analisador(CHAVE).nome()).isEqualTo("llm");
    }

    @Test
    @DisplayName("resposta boa vira ResultadoDaAnalise, e o pedido sai no formato da API de mensagens")
    void respostaBoaViraResultado() {
        corpoDaResposta = envelope(vereditoEmJson(true, 88, "Critérios atendidos: Java, Inglês."));

        ResultadoDaAnalise resultado = analisador(CHAVE).analisar(CONTEXTO);

        assertThat(resultado.aprovado()).isTrue();
        assertThat(resultado.pontuacao()).isEqualTo(88);
        assertThat(resultado.motivo()).isEqualTo("Critérios atendidos: Java, Inglês.");
        assertThat(resultado.criteriosAtendidos()).containsExactly("Java", "Inglês");
        assertThat(resultado.criteriosNaoAtendidos()).isEmpty();

        Recebido pedido = recebidos.getFirst();
        assertThat(pedido.metodo()).isEqualTo("POST");
        assertThat(pedido.caminho()).isEqualTo("/v1/messages");
        assertThat(pedido.chave()).isEqualTo(CHAVE);
        assertThat(pedido.versao()).isNotBlank();

        Map<String, Object> corpo = JSON.readValue(pedido.corpo(), Map.class);
        assertThat(corpo.get("model")).isEqualTo(MODELO);
        assertThat(corpo.get("max_tokens")).isInstanceOf(Integer.class);
        assertThat((String) corpo.get("system")).contains("JSON");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> mensagens = (List<Map<String, Object>>) corpo.get("messages");
        assertThat(mensagens).hasSize(1);
        assertThat(mensagens.getFirst().get("role")).isEqualTo("user");
        // O prompt tem que levar vaga, criterios e curriculo: sem os tres a
        // LLM nao tem como decidir nada.
        assertThat((String) mensagens.getFirst().get("content"))
                .contains("Pessoa Desenvolvedora Java")
                .contains("Spring Boot e PostgreSQL")
                .contains("Java")
                .contains("obrigatório")
                .contains("Carlos Pereira");
    }

    @Test
    @DisplayName("reprovacao vem com os criterios que faltaram")
    void respostaDeReprovacao() {
        corpoDaResposta = envelope("""
                {"aprovado": false, "pontuacao": 50, "motivo": "Critérios não atendidos: Inglês.",
                 "criteriosAtendidos": ["Java"], "criteriosNaoAtendidos": ["Inglês"]}
                """);

        ResultadoDaAnalise resultado = analisador(CHAVE).analisar(CONTEXTO);

        assertThat(resultado.aprovado()).isFalse();
        assertThat(resultado.pontuacao()).isEqualTo(50);
        assertThat(resultado.criteriosNaoAtendidos()).containsExactly("Inglês");
    }

    @Test
    @DisplayName("JSON dentro de cerca de codigo tambem e aceito")
    void jsonEmCercaDeCodigo() {
        corpoDaResposta = envelope("""
                Claro! Segue a análise:
                ```json
                {"aprovado": true, "pontuacao": 70, "motivo": "Atende o essencial.",
                 "criteriosAtendidos": ["Java"], "criteriosNaoAtendidos": ["Inglês"]}
                ```
                """);

        ResultadoDaAnalise resultado = analisador(CHAVE).analisar(CONTEXTO);

        assertThat(resultado.pontuacao()).isEqualTo(70);
        assertThat(resultado.aprovado()).isTrue();
    }

    @Test
    @DisplayName("aprovar abaixo da pontuacao minima da vaga nao vale: a regra e do RH")
    void aprovadoAbaixoDaMinimaNaoPassa() {
        corpoDaResposta = envelope(vereditoEmJson(true, 10, "Gostei do candidato."));

        ResultadoDaAnalise resultado = analisador(CHAVE).analisar(CONTEXTO);

        assertThat(resultado.pontuacao()).isEqualTo(10);
        assertThat(resultado.aprovado()).as("10 nao alcanca a minima 60 da vaga").isFalse();
    }

    @Test
    @DisplayName("texto que nao e JSON estoura em vez de aprovar por engano")
    void textoQueNaoEhJsonEstoura() {
        corpoDaResposta = envelope("Desculpe, não posso avaliar esse currículo.");

        assertThatThrownBy(() -> analisador(CHAVE).analisar(CONTEXTO))
                .isInstanceOf(AnaliseIndisponivelException.class);
    }

    @Test
    @DisplayName("JSON sem os campos obrigatorios estoura")
    void jsonIncompletoEstoura() {
        corpoDaResposta = envelope("{\"pontuacao\": 90}");

        assertThatThrownBy(() -> analisador(CHAVE).analisar(CONTEXTO))
                .isInstanceOf(AnaliseIndisponivelException.class)
                .hasMessageContaining("aprovado");
    }

    @Test
    @DisplayName("pontuacao fora de 0 a 100 estoura")
    void pontuacaoForaDaFaixaEstoura() {
        corpoDaResposta = envelope(vereditoEmJson(true, 180, "Excelente."));

        assertThatThrownBy(() -> analisador(CHAVE).analisar(CONTEXTO))
                .isInstanceOf(AnaliseIndisponivelException.class);
    }

    @Test
    @DisplayName("envelope sem bloco de texto estoura")
    void envelopeVazioEstoura() {
        corpoDaResposta = "{\"content\": []}";

        assertThatThrownBy(() -> analisador(CHAVE).analisar(CONTEXTO))
                .isInstanceOf(AnaliseIndisponivelException.class);
    }

    @Test
    @DisplayName("erro HTTP da API estoura")
    void erroHttpEstoura() {
        status = 500;
        corpoDaResposta = "{\"type\": \"error\", \"error\": {\"type\": \"api_error\"}}";

        assertThatThrownBy(() -> analisador(CHAVE).analisar(CONTEXTO))
                .isInstanceOf(AnaliseIndisponivelException.class);
    }

    @Test
    @DisplayName("sem api-key o cliente nasce igual: quem falha e a chamada, nao o boot")
    void semChaveOClienteNasceMasAChamadaFalha() {
        // A chave e vazia por default em application.yml. Se o construtor a
        // exigisse, a aplicacao nao subiria sem credencial de LLM nem para
        // usar a estrategia textual.
        AnalisadorViaLlm semChave = analisador("");

        assertThatThrownBy(() -> semChave.analisar(CONTEXTO))
                .isInstanceOf(AnaliseIndisponivelException.class);
        assertThat(recebidos).hasSize(1);
        assertThat(recebidos.getFirst().chave()).as("nenhum cabecalho x-api-key foi enviado").isNull();
    }

    @Test
    @DisplayName("servidor fora do ar estoura: o job volta para a fila em vez de aprovar")
    void servidorForaDoArEstoura() {
        // Porta 1: ninguem escuta, a conexao e recusada na hora. E a falha de
        // verdade, sem nenhum duble.
        AnalisadorViaLlm semServidor = new AnalisadorViaLlm(new ClienteLlmHttp(
                new PropriedadesLlm("http://127.0.0.1:1", CHAVE, MODELO, Duration.ofSeconds(2))));

        assertThatThrownBy(() -> semServidor.analisar(CONTEXTO))
                .isInstanceOf(AnaliseIndisponivelException.class);
        assertThat(recebidos).isEmpty();
    }

    private AnalisadorViaLlm analisador(String chave) {
        return new AnalisadorViaLlm(new ClienteLlmHttp(
                new PropriedadesLlm(enderecoDoServidor(), chave, MODELO, Duration.ofSeconds(5))));
    }

    private String enderecoDoServidor() {
        return "http://127.0.0.1:" + servidor.getAddress().getPort();
    }

    private void responder(HttpExchange troca) throws IOException {
        String corpo = new String(troca.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String chave = troca.getRequestHeaders().getFirst("x-api-key");
        recebidos.add(new Recebido(troca.getRequestMethod(),
                troca.getRequestURI().getPath(),
                chave,
                troca.getRequestHeaders().getFirst("anthropic-version"),
                corpo));

        // Como a API de verdade: sem credencial, 401.
        boolean autenticado = chave != null && !chave.isBlank();
        byte[] saida = (autenticado ? corpoDaResposta : "{\"type\": \"error\"}").getBytes(StandardCharsets.UTF_8);
        troca.getResponseHeaders().set("Content-Type", "application/json");
        troca.sendResponseHeaders(autenticado ? status : 401, saida.length);
        try (var fluxo = troca.getResponseBody()) {
            fluxo.write(saida);
        }
    }

    /** Envelope de resposta da API de mensagens, com o texto do modelo dentro. */
    private static String envelope(String texto) {
        return """
                {"id": "msg_01", "type": "message", "role": "assistant", "model": "%s",
                 "content": [{"type": "text", "text": %s}],
                 "usage": {"input_tokens": 10, "output_tokens": 20}}
                """.formatted(MODELO, JSON.writeValueAsString(texto));
    }

    private static String vereditoEmJson(boolean aprovado, int pontuacao, String motivo) {
        return """
                {"aprovado": %s, "pontuacao": %d, "motivo": %s,
                 "criteriosAtendidos": ["Java", "Inglês"], "criteriosNaoAtendidos": []}
                """.formatted(aprovado, pontuacao, JSON.writeValueAsString(motivo));
    }
}
