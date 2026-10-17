package com.gupyclone.suporte;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

/**
 * Zera os dados transacionais entre testes, preservando as personas semeadas
 * pela migracao V2.
 *
 * <p>Truncate em vez de transacao com rollback de proposito: varios testes
 * precisam de concorrencia real e de requisicoes HTTP reais, que nao convivem
 * com o rollback do {@code @Transactional} aplicado ao metodo de teste.
 */
public class LimpadorDeBanco {

    private final JdbcClient jdbc;

    public LimpadorDeBanco(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void limpar() {
        jdbc.sql("""
                truncate table email_outbox,
                               analise_job,
                               candidatura,
                               curriculo,
                               criterio_aceite,
                               vaga
                         restart identity cascade
                """).update();
    }
}
