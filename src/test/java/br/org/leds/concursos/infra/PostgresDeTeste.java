package br.org.leds.concursos.infra;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Fornece o PostgreSQL usado nos testes de integração.
 *
 * <p>Por padrão sobe um container (Testcontainers) compartilhado por todos os testes. Se a
 * variável {@code TEST_DATABASE_URL} estiver definida, usa esse banco externo
 * (útil em ambientes sem Docker); usuário/senha vêm de {@code TEST_DATABASE_USER}/
 * {@code TEST_DATABASE_PASSWORD} (padrão postgres/postgres).</p>
 */
public final class PostgresDeTeste {

    private static PostgreSQLContainer<?> container;

    private PostgresDeTeste() {
    }

    public static void registrar(DynamicPropertyRegistry registro) {
        String urlExterna = System.getenv("TEST_DATABASE_URL");
        if (urlExterna != null && !urlExterna.isBlank()) {
            registro.add("spring.datasource.url", () -> urlExterna);
            registro.add("spring.datasource.username", () -> env("TEST_DATABASE_USER", "postgres"));
            registro.add("spring.datasource.password", () -> env("TEST_DATABASE_PASSWORD", "postgres"));
        } else {
            PostgreSQLContainer<?> pg = iniciarContainer();
            registro.add("spring.datasource.url", pg::getJdbcUrl);
            registro.add("spring.datasource.username", pg::getUsername);
            registro.add("spring.datasource.password", pg::getPassword);
        }
        registro.add("app.importacao.habilitada", () -> "false");
    }

    private static synchronized PostgreSQLContainer<?> iniciarContainer() {
        if (container == null) {
            container = new PostgreSQLContainer<>("postgres:16-alpine");
            container.start();
        }
        return container;
    }

    private static String env(String nome, String padrao) {
        String valor = System.getenv(nome);
        return valor == null || valor.isBlank() ? padrao : valor;
    }
}
