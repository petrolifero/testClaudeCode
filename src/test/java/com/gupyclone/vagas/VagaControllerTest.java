package com.gupyclone.vagas;

import static org.assertj.core.api.Assertions.assertThat;

import com.gupyclone.pessoas.RecrutadorRepository;
import com.gupyclone.suporte.TesteDeIntegracao;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Prova da publicacao de vagas pela API: HTTP real, PostgreSQL real e a
 * autorizacao por papel valendo de verdade.
 */
class VagaControllerTest extends TesteDeIntegracao {

    private static final String VAGA_JAVA = """
            {
              "titulo": "Pessoa Desenvolvedora Java",
              "descricao": "Time de plataforma: Spring Boot e PostgreSQL",
              "pontuacaoMinima": 60,
              "criterios": [
                {"descricao": "Java", "expressao": "java", "peso": 50, "obrigatorio": true},
                {"descricao": "PostgreSQL", "expressao": "postgresql or postgres", "peso": 30, "obrigatorio": false}
              ]
            }
            """;

    @Autowired
    private ServicoDeVagas servico;

    @Autowired
    private VagaRepository vagas;

    @Autowired
    private CriterioAceiteRepository criterios;

    @Autowired
    private RecrutadorRepository recrutadores;

    @Autowired
    private JdbcClient jdbc;

    private Long ana;

    @BeforeEach
    void localizarRecrutadora() {
        ana = recrutadores.findByChave("ana").orElseThrow().getId();
    }

    @Test
    @DisplayName("Ana publica uma vaga com criterios e recebe 201 com Location")
    void anaPublicaVagaComCriterios() {
        clienteComo("ana").post().uri("/api/vagas")
                .contentType(MediaType.APPLICATION_JSON)
                .body(VAGA_JAVA)
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().valueMatches(HttpHeaders.LOCATION, "/api/vagas/\\d+")
                .expectBody()
                .jsonPath("$.id").isNumber()
                .jsonPath("$.titulo").isEqualTo("Pessoa Desenvolvedora Java")
                .jsonPath("$.descricao").isEqualTo("Time de plataforma: Spring Boot e PostgreSQL")
                .jsonPath("$.status").isEqualTo("ABERTA")
                .jsonPath("$.pontuacaoMinima").isEqualTo(60)
                .jsonPath("$.recrutador.id").isEqualTo(ana.intValue())
                .jsonPath("$.recrutador.nome").isEqualTo("Ana Souza")
                .jsonPath("$.criadoEm").isNotEmpty()
                .jsonPath("$.criterios.length()").isEqualTo(2)
                .jsonPath("$.criterios[0].descricao").isEqualTo("Java")
                .jsonPath("$.criterios[0].expressao").isEqualTo("java")
                .jsonPath("$.criterios[0].peso").isEqualTo(50)
                .jsonPath("$.criterios[0].obrigatorio").isEqualTo(true)
                .jsonPath("$.criterios[1].obrigatorio").isEqualTo(false);
    }

    @Test
    @DisplayName("os criterios publicados ficam gravados com peso e obrigatoriedade")
    void criteriosFicamGravados() {
        clienteComo("ana").post().uri("/api/vagas")
                .contentType(MediaType.APPLICATION_JSON)
                .body(VAGA_JAVA)
                .exchange()
                .expectStatus().isCreated();

        assertThat(vagas.count()).isEqualTo(1);
        Long vagaId = vagas.findAll().getFirst().getId();

        assertThat(jdbc.sql("select recrutador_id from vaga where id = ?").param(vagaId)
                .query(Long.class).single()).isEqualTo(ana);

        var gravados = criterios.findAllByVagaIdOrderByIdAsc(vagaId);
        assertThat(gravados).extracting(CriterioAceite::getDescricao).containsExactly("Java", "PostgreSQL");
        assertThat(gravados).extracting(CriterioAceite::getExpressao)
                .containsExactly("java", "postgresql or postgres");
        assertThat(gravados).extracting(CriterioAceite::getPeso).containsExactly(50, 30);
        assertThat(gravados).extracting(CriterioAceite::isObrigatorio).containsExactly(true, false);
    }

    @Test
    @DisplayName("Carlos, candidato, nao publica vaga")
    void candidatoNaoPublica() {
        clienteComo("carlos").post().uri("/api/vagas")
                .contentType(MediaType.APPLICATION_JSON)
                .body(VAGA_JAVA)
                .exchange()
                .expectStatus().isForbidden();

        assertThat(vagas.count()).isZero();
    }

    @Test
    @DisplayName("deslogado nao publica nem lista vagas")
    void anonimoNaoPublicaNemLista() {
        clienteAnonimo().post().uri("/api/vagas")
                .contentType(MediaType.APPLICATION_JSON)
                .body(VAGA_JAVA)
                .exchange()
                .expectStatus().isUnauthorized();

        clienteAnonimo().get().uri("/api/vagas").exchange().expectStatus().isUnauthorized();
        clienteAnonimo().get().uri("/api/vagas/1").exchange().expectStatus().isUnauthorized();

        assertThat(vagas.count()).isZero();
    }

    @Test
    @DisplayName("a listagem traz somente vagas abertas, das mais recentes para as mais antigas")
    void listaSomenteVagasAbertas() {
        Long antiga = publicar("Analista de dados");
        Long recente = publicar("Pessoa Desenvolvedora Java");
        Long fechada = publicar("Vaga que foi fechada");
        servico.fechar(fechada, ana);
        envelhecer(antiga);

        clienteComo("carlos").get().uri("/api/vagas").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(2)
                .jsonPath("$[0].id").isEqualTo(recente.intValue())
                .jsonPath("$[1].id").isEqualTo(antiga.intValue())
                .jsonPath("$[0].criterios.length()").isEqualTo(1);
    }

    @Test
    @DisplayName("a vaga fechada continua acessivel por id, com o status atualizado")
    void vagaFechadaAcessivelPorId() {
        Long vaga = publicar("Vaga que foi fechada");
        servico.fechar(vaga, ana);

        clienteComo("joao").get().uri("/api/vagas/" + vaga).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(vaga.intValue())
                .jsonPath("$.status").isEqualTo("FECHADA");
    }

    @Test
    @DisplayName("so o RH dono recebe a expressao de busca dos criterios")
    void expressaoSoVaiParaORhDono() {
        // A expressao e o criterio de triagem em forma executavel: entregue ao
        // candidato, diria o que escrever no curriculo para passar. Com o
        // recrutador agora guardado por id, e o id que decide quem e o dono.
        Long vaga = publicar("Pessoa Desenvolvedora Java");

        clienteComo("carlos").get().uri("/api/vagas/" + vaga).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.recrutador.nome").isEqualTo("Ana Souza")
                .jsonPath("$.criterios[0].descricao").isEqualTo("Java")
                .jsonPath("$.criterios[0].expressao").isEmpty();

        clienteComo("carlos").get().uri("/api/vagas").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].criterios[0].expressao").isEmpty();

        clienteComo("ana").get().uri("/api/vagas/" + vaga).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.recrutador.nome").isEqualTo("Ana Souza")
                .jsonPath("$.criterios[0].expressao").isEqualTo("java");
    }

    @Test
    @DisplayName("vaga inexistente responde 404")
    void vagaInexistenteResponde404() {
        clienteComo("carlos").get().uri("/api/vagas/9999999").exchange()
                .expectStatus().isNotFound()
                .expectBody().jsonPath("$.codigo").isEqualTo("vaga_inexistente");
    }

    @Test
    @DisplayName("recusa vaga sem titulo")
    void recusaVagaSemTitulo() {
        recusa("""
                {
                  "titulo": "   ",
                  "descricao": "Time de plataforma",
                  "pontuacaoMinima": 60,
                  "criterios": [{"descricao": "Java", "expressao": "java", "peso": 10, "obrigatorio": true}]
                }
                """);
    }

    @Test
    @DisplayName("recusa peso de criterio fora de 1..100")
    void recusaPesoForaDaFaixa() {
        recusa(comPeso(0));
        recusa(comPeso(101));
    }

    @Test
    @DisplayName("recusa pontuacao minima fora de 0..100")
    void recusaPontuacaoMinimaForaDaFaixa() {
        recusa("""
                {
                  "titulo": "Pessoa Desenvolvedora Java",
                  "descricao": "Time de plataforma",
                  "pontuacaoMinima": 101,
                  "criterios": [{"descricao": "Java", "expressao": "java", "peso": 10, "obrigatorio": true}]
                }
                """);
    }

    private static String comPeso(int peso) {
        return """
                {
                  "titulo": "Pessoa Desenvolvedora Java",
                  "descricao": "Time de plataforma",
                  "pontuacaoMinima": 60,
                  "criterios": [{"descricao": "Java", "expressao": "java", "peso": %d, "obrigatorio": true}]
                }
                """.formatted(peso);
    }

    private void recusa(String corpo) {
        clienteComo("ana").post().uri("/api/vagas")
                .contentType(MediaType.APPLICATION_JSON)
                .body(corpo)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.codigo").isEqualTo("dados_invalidos");

        assertThat(vagas.count()).isZero();
    }

    private Long publicar(String titulo) {
        return servico.publicar(ana, titulo, "Descricao de " + titulo, 50,
                List.of(new ServicoDeVagas.NovoCriterio("Java", "java", 10, true))).getId();
    }

    /**
     * Empurra a vaga um dia para tras no banco: sem isso duas vagas criadas no
     * mesmo teste podem compartilhar o instante de criacao e a ordenacao da
     * listagem ficaria indefinida.
     */
    private void envelhecer(Long vagaId) {
        jdbc.sql("update vaga set criado_em = criado_em - interval '1 day' where id = ?")
                .param(vagaId).update();
    }
}
