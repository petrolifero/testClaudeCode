package br.leds.comum.jdbc;

import javax.sql.DataSource;
import org.postgresql.ds.PGSimpleDataSource;

/** DataSource sem pool, suficiente para a CLI (uma execução = poucas conexões). A API usa um pool próprio. */
public final class FabricaDataSource {

    private FabricaDataSource() {
    }

    public static DataSource simples(ConfiguracaoBanco configuracao) {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(configuracao.url());
        ds.setUser(configuracao.usuario());
        ds.setPassword(configuracao.senha());
        return ds;
    }
}
