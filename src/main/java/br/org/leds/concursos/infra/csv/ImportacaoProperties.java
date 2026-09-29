package br.org.leds.concursos.infra.csv;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

/**
 * Configuração da carga inicial ({@code app.importacao.*}).
 *
 * @param habilitada se a carga deve rodar na inicialização
 * @param candidatos local do arquivo de candidatos (classpath:, file:, ...)
 * @param concursos  local do arquivo de concursos
 */
@ConfigurationProperties(prefix = "app.importacao")
public record ImportacaoProperties(boolean habilitada, Resource candidatos, Resource concursos) {
}
