package br.leds.comum;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/** Abre conexões JDBC a partir de variáveis de ambiente (com padrões que casam com o docker-compose). */
public final class Banco {

    private final String url;
    private final String usuario;
    private final String senha;

    public Banco(String nomeBancoPadrao) {
        this.url = ambiente("DB_URL", "jdbc:postgresql://localhost:5432/" + nomeBancoPadrao + "?reWriteBatchedInserts=true");
        this.usuario = ambiente("DB_USER", "leds");
        this.senha = ambiente("DB_PASSWORD", "leds");
    }

    public Connection abrir() {
        try {
            return DriverManager.getConnection(url, usuario, senha);
        } catch (SQLException e) {
            throw new ErroAplicacao("Não foi possível conectar em " + url + ": " + e.getMessage(), e);
        }
    }

    private static String ambiente(String nome, String padrao) {
        String valor = System.getenv(nome);
        return valor == null || valor.isBlank() ? padrao : valor;
    }
}
