package br.leds.api;

import br.leds.comum.aplicacao.ConsultasConcursos;
import br.leds.comum.aplicacao.Modelagem;
import br.leds.comum.aplicacao.ServicoConsultas;
import br.leds.comum.jdbc.ConfiguracaoBanco;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Raiz de composição da API. É aqui (e só aqui) que a modelagem escolhida vira beans; o controller enxerga
 * apenas {@link ServicoConsultas}, que por sua vez só conhece a porta {@link ConsultasConcursos}.
 */
@Configuration
class ConfiguracaoAplicacao {

    @Bean
    Modelagem modelagem(@Value("${concursos.modelagem}") String nome) {
        return SelecaoModelagem.escolher(nome);
    }

    /** Pool de conexões: ao contrário da CLI, a API atende várias requisições concorrentes. */
    @Bean(destroyMethod = "close")
    DataSource dataSource(Modelagem modelagem) {
        ConfiguracaoBanco configuracao = ConfiguracaoBanco.doAmbiente(modelagem.nome());
        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl(configuracao.url());
        hikari.setUsername(configuracao.usuario());
        hikari.setPassword(configuracao.senha());
        hikari.setPoolName("concursos-" + modelagem.nome());
        hikari.setInitializationFailTimeout(-1); // sobe mesmo sem banco; falhas viram 503 por requisição
        return new HikariDataSource(hikari);
    }

    @Bean
    ConsultasConcursos consultasConcursos(Modelagem modelagem, DataSource dataSource) {
        return modelagem.consultas(dataSource);
    }

    @Bean
    ServicoConsultas servicoConsultas(ConsultasConcursos consultas) {
        return new ServicoConsultas(consultas);
    }
}
