package br.org.leds.concursos.infra;

import br.org.leds.concursos.aplicacao.BuscaRepository;
import br.org.leds.concursos.aplicacao.CandidatoCompativel;
import br.org.leds.concursos.aplicacao.ConcursoCompativel;
import br.org.leds.concursos.aplicacao.ImportacaoRepository;
import br.org.leds.concursos.dominio.Candidato;
import br.org.leds.concursos.dominio.CodigoConcurso;
import br.org.leds.concursos.dominio.Concurso;
import br.org.leds.concursos.dominio.Cpf;
import br.org.leds.concursos.infra.csv.ImportadorInicial;
import br.org.leds.concursos.infra.csv.LeitorArquivos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Carrega os arquivos oficiais no PostgreSQL e confere <strong>todas</strong> as buscas possíveis
 * (1000 CPFs e todos os códigos) contra um oráculo calculado em memória, por força bruta.
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BuscaRepositoryIntegracaoTest {

    @DynamicPropertySource
    static void banco(DynamicPropertyRegistry registro) {
        PostgresDeTeste.registrar(registro);
    }

    @Autowired
    private BuscaRepository busca;
    @Autowired
    private ImportacaoRepository importacao;
    @Autowired
    private ImportadorInicial importador;
    @Autowired
    private BancoDeTeste banco;

    private List<Candidato> candidatos;
    private List<Concurso> concursos;

    @BeforeAll
    void carregar() throws IOException {
        banco.limpar();
        ClassPathResource arqCandidatos = new ClassPathResource("dados/candidatos.txt");
        ClassPathResource arqConcursos = new ClassPathResource("dados/concursos.txt");
        importador.importar(arqCandidatos, arqConcursos);

        LeitorArquivos leitor = new LeitorArquivos();
        try (Reader c = leitorDe(arqCandidatos); Reader k = leitorDe(arqConcursos)) {
            candidatos = leitor.lerCandidatos(c, "candidatos.txt");
            concursos = leitor.lerConcursos(k, "concursos.txt");
        }
    }

    @Test
    void importacaoMarcaQueOBancoPossuiDados() {
        assertThat(importacao.possuiDados()).isTrue();
    }

    @Test
    void existenciaDeCandidatoEConcurso() {
        assertThat(busca.existeCandidato(candidatos.getFirst().cpf())).isTrue();
        assertThat(busca.existeCandidato(Cpf.of("529.982.247-25"))).isFalse();
        assertThat(busca.existeConcurso(concursos.getFirst().codigo())).isTrue();
        assertThat(busca.existeConcurso(CodigoConcurso.of("00000000000"))).isFalse();
    }

    @Test
    void busca1ConfereComOraculoParaTodosOsCandidatos() {
        for (Candidato candidato : candidatos) {
            assertThat(busca.concursosCompativeis(candidato.cpf()))
                    .as("concursos do CPF %s", candidato.cpf())
                    .containsExactlyElementsOf(esperadoConcursos(candidato));
        }
    }

    @Test
    void busca2ConfereComOraculoParaTodosOsCodigos() {
        concursos.stream().map(Concurso::codigo).distinct().forEach(codigo ->
                assertThat(busca.candidatosCompativeis(codigo))
                        .as("candidatos do código %s", codigo)
                        .containsExactlyElementsOf(esperadoCandidatos(codigo)));
    }

    private List<ConcursoCompativel> esperadoConcursos(Candidato candidato) {
        return concursos.stream()
                .filter(c -> !Collections.disjoint(c.vagas(), candidato.profissoes()))
                .map(c -> new ConcursoCompativel(c.orgao(), c.codigo().valor(), c.edital()))
                .distinct()
                .sorted(Comparator.comparing(ConcursoCompativel::orgao)
                        .thenComparing(ConcursoCompativel::codigo)
                        .thenComparing(ConcursoCompativel::edital))
                .toList();
    }

    private List<CandidatoCompativel> esperadoCandidatos(CodigoConcurso codigo) {
        var vagas = concursos.stream()
                .filter(c -> c.codigo().equals(codigo))
                .flatMap(c -> c.vagas().stream())
                .collect(Collectors.toSet());
        return candidatos.stream()
                .filter(c -> !Collections.disjoint(c.profissoes(), vagas))
                .sorted(Comparator.comparing(Candidato::nome).thenComparing(c -> c.cpf().digitos()))
                .map(c -> new CandidatoCompativel(c.nome(), c.dataNascimento(), c.cpf().formatado()))
                .toList();
    }

    private static Reader leitorDe(ClassPathResource recurso) throws IOException {
        return new InputStreamReader(recurso.getInputStream(), StandardCharsets.UTF_8);
    }
}
