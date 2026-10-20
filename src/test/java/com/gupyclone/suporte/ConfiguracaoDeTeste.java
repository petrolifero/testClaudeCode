package com.gupyclone.suporte;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Componentes auxiliares disponiveis em todos os testes de integracao. */
@TestConfiguration
public class ConfiguracaoDeTeste {

    @Bean
    LimpadorDeBanco limpadorDeBanco(JdbcClient jdbc) {
        return new LimpadorDeBanco(jdbc);
    }
}
