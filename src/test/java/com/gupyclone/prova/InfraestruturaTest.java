package com.gupyclone.prova;

import static org.assertj.core.api.Assertions.assertThat;

import com.gupyclone.seguranca.Papel;
import com.gupyclone.suporte.TesteDeIntegracao;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestConstructor;

/**
 * Prova de infraestrutura: PostgreSQL em container, migracoes aplicadas,
 * personas semeadas e a autorizacao por papel de pe.
 */
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class InfraestruturaTest extends TesteDeIntegracao {

    @Autowired
    private JdbcClient jdbc;

    @Test
    @DisplayName("as migracoes Flyway rodaram no PostgreSQL real")
    void migracoesAplicadas() {
        Integer versoes = jdbc.sql("select count(*) from flyway_schema_history where success").query(Integer.class).single();
        assertThat(versoes).isGreaterThanOrEqualTo(2);

        Integer config = jdbc.sql("select count(*) from pg_ts_config where cfgname = 'portugues_sem_acento'")
                .query(Integer.class).single();
        assertThat(config).as("configuracao de busca textual em portugues sem acento").isEqualTo(1);
    }

    @Test
    @DisplayName("as tres personas do enunciado estao disponiveis")
    void personasSemeadas() {
        var disponiveis = personas.disponiveis();

        assertThat(disponiveis).hasSize(3);
        assertThat(disponiveis).extracting("chave").containsExactlyInAnyOrder("ana", "carlos", "joao");
        assertThat(personas.resolver("ana").papel()).isEqualTo(Papel.RH);
        assertThat(personas.resolver("carlos").papel()).isEqualTo(Papel.CANDIDATO);
        assertThat(personas.resolver("joao").papel()).isEqualTo(Papel.CANDIDATO);
    }

    @Test
    @DisplayName("deslogado nao acessa a API; logado acessa")
    void autorizacaoPorPapel() {
        clienteAnonimo().get().uri("/api/eu").exchange().expectStatus().isUnauthorized();

        clienteComo("ana").get().uri("/api/eu").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.chave").isEqualTo("ana")
                .jsonPath("$.papel").isEqualTo("RH");
    }

    @Test
    @DisplayName("o token emitido e lido de volta com papel e entidade")
    void tokenIdaEVolta() {
        String token = tokenDe("carlos");

        var usuario = tokens.ler(token);

        assertThat(usuario.chave()).isEqualTo("carlos");
        assertThat(usuario.papel()).isEqualTo(Papel.CANDIDATO);
        assertThat(usuario.entidadeId()).isNotNull();
    }
}
