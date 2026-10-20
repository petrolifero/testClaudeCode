package com.gupyclone.suporte;

import com.gupyclone.seguranca.ServicoDePersonas;
import com.gupyclone.seguranca.ServicoDeToken;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base dos testes de integracao.
 *
 * <p>Nao existe nenhuma biblioteca de mocks no projeto (o Mockito e excluido
 * no pom de proposito): aqui sobe um PostgreSQL real em container, com as
 * migracoes Flyway de verdade e a aplicacao servindo HTTP em porta real. O
 * container e iniciado uma unica vez por JVM (padrao singleton container) e
 * reaproveitado por todas as classes de teste.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(ConfiguracaoDeTeste.class)
public abstract class TesteDeIntegracao {

    @ServiceConnection
    protected static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @LocalServerPort
    protected int porta;

    @Autowired
    protected ServicoDeToken tokens;

    @Autowired
    protected ServicoDePersonas personas;

    @Autowired
    private LimpadorDeBanco limpador;

    @BeforeEach
    void limparBancoAntesDeCadaTeste() {
        limpador.limpar();
    }

    protected String enderecoBase() {
        return "http://localhost:" + porta;
    }

    /** Cliente sem autenticacao: e o visitante deslogado. */
    protected RestTestClient clienteAnonimo() {
        return RestTestClient.bindToServer().baseUrl(enderecoBase()).build();
    }

    /** Cliente autenticado como a persona informada, via Authorization: Bearer. */
    protected RestTestClient clienteComo(String chave) {
        return RestTestClient.bindToServer()
                .baseUrl(enderecoBase())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDe(chave))
                .build();
    }

    protected String tokenDe(String chave) {
        return tokens.emitir(personas.resolver(chave));
    }
}
