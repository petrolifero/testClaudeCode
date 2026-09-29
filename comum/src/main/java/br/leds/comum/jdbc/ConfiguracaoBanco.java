package br.leds.comum.jdbc;

/** Parâmetros de conexão. Só dados: quem cria o {@code DataSource} (simples na CLI, pool na API) decide o resto. */
public record ConfiguracaoBanco(String url, String usuario, String senha) {

    /**
     * Lê {@code DB_URL}, {@code DB_USER} e {@code DB_PASSWORD}; os padrões casam com o docker-compose.
     *
     * @param nomeBancoPadrao banco usado quando {@code DB_URL} não é informada
     */
    public static ConfiguracaoBanco doAmbiente(String nomeBancoPadrao) {
        return new ConfiguracaoBanco(
                ambiente("DB_URL", "jdbc:postgresql://localhost:5432/" + nomeBancoPadrao + "?reWriteBatchedInserts=true"),
                ambiente("DB_USER", "leds"),
                ambiente("DB_PASSWORD", "leds"));
    }

    private static String ambiente(String nome, String padrao) {
        String valor = System.getenv(nome);
        return valor == null || valor.isBlank() ? padrao : valor;
    }
}
