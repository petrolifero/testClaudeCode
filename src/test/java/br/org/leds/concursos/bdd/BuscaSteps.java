package br.org.leds.concursos.bdd;

import br.org.leds.concursos.aplicacao.ImportacaoRepository;
import br.org.leds.concursos.dominio.Candidato;
import br.org.leds.concursos.dominio.CodigoConcurso;
import br.org.leds.concursos.dominio.Concurso;
import br.org.leds.concursos.dominio.Cpf;
import br.org.leds.concursos.dominio.Profissoes;
import br.org.leds.concursos.infra.BancoDeTeste;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.Before;
import io.cucumber.java.pt.Dado;
import io.cucumber.java.pt.E;
import io.cucumber.java.pt.Então;
import io.cucumber.java.pt.Quando;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class BuscaSteps {

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @LocalServerPort
    private int porta;
    @Autowired
    private ImportacaoRepository importacao;
    @Autowired
    private BancoDeTeste banco;
    @Autowired
    private ObjectMapper json;

    private ResponseEntity<String> resposta;

    @Before
    public void limparBanco() {
        banco.limpar();
    }

    @Dado("os candidatos cadastrados:")
    public void osCandidatos(DataTable tabela) {
        importacao.salvarCandidatos(tabela.asMaps().stream()
                .map(l -> new Candidato(l.get("Nome"), LocalDate.parse(l.get("Data de Nascimento"), DATA),
                        Cpf.of(l.get("CPF")), Profissoes.interpretar(l.get("Profissões"))))
                .toList());
    }

    @E("os concursos cadastrados:")
    public void osConcursos(DataTable tabela) {
        importacao.salvarConcursos(tabela.asMaps().stream()
                .map(l -> new Concurso(l.get("Órgão"), l.get("Edital"),
                        CodigoConcurso.of(l.get("Código do Concurso")), Profissoes.interpretar(l.get("Lista de vagas"))))
                .toList());
    }

    @Quando("eu buscar os concursos do candidato de CPF {string}")
    public void buscarConcursos(String cpf) {
        resposta = get("/api/candidatos/" + cpf + "/concursos");
    }

    @Quando("eu buscar os candidatos do concurso de código {string}")
    public void buscarCandidatos(String codigo) {
        resposta = get("/api/concursos/" + codigo + "/candidatos");
    }

    @Então("devo receber os concursos:")
    @Então("devo receber os candidatos:")
    public void devoReceber(DataTable esperado) throws Exception {
        assertThat(resposta.getStatusCode().value()).isEqualTo(200);
        assertThat(corpoComoLista()).containsExactlyElementsOf(esperado.asMaps());
    }

    @Então("devo receber uma lista vazia")
    public void listaVazia() throws Exception {
        assertThat(resposta.getStatusCode().value()).isEqualTo(200);
        assertThat(corpoComoLista()).isEmpty();
    }

    @Então("devo receber o erro {int} com a mensagem {string}")
    public void erro(int status, String mensagem) throws Exception {
        assertThat(resposta.getStatusCode().value()).isEqualTo(status);
        assertThat(json.readTree(resposta.getBody()).get("detail").asText()).contains(mensagem);
    }

    private List<Map<String, String>> corpoComoLista() throws Exception {
        return json.readValue(resposta.getBody(), new TypeReference<>() {
        });
    }

    private ResponseEntity<String> get(String caminho) {
        return RestClient.create("http://localhost:" + porta).get().uri(caminho)
                .retrieve()
                .onStatus(s -> true, (req, res) -> { })
                .toEntity(String.class);
    }
}
