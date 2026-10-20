package com.gupyclone.suporte;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

/**
 * Devolve o banco ao estado das migracoes entre testes.
 *
 * <p>Truncate em vez de transacao com rollback de proposito: varios testes
 * precisam de concorrencia real e de requisicoes HTTP reais, que nao convivem
 * com o rollback do {@code @Transactional} aplicado ao metodo de teste.
 *
 * <p>As pessoas tambem voltam ao estado da migracao V2, e nao apenas "nao sao
 * truncadas": pessoas criadas por um teste (a segunda recrutadora, os
 * candidatos concorrentes) sobreviviam a limpeza e vazavam para os testes
 * seguintes. Isso tornava a suite dependente da ordem das classes — um teste
 * que afirma "o cabecalho oferece exatamente 3 pessoas" passava ou falhava
 * conforme quem rodasse antes. Foi assim que a CI pegou uma falha que a
 * execucao local nao mostrava.
 */
public class LimpadorDeBanco {

    private final JdbcClient jdbc;

    public LimpadorDeBanco(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Chaves das personas semeadas pela migracao V2; so elas sobrevivem. */
    private static final String PERSONAS_DA_MIGRACAO = "('ana', 'carlos', 'joao')";

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
        // Depois do truncate acima nao resta nada apontando para estas tabelas,
        // por isso o delete e seguro. Os testes que precisam de gente extra a
        // recriam com findByChave(...).orElseGet(...).
        jdbc.sql("delete from candidato where chave not in " + PERSONAS_DA_MIGRACAO).update();
        jdbc.sql("delete from recrutador where chave not in " + PERSONAS_DA_MIGRACAO).update();
    }
}
