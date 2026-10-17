package com.gupyclone.notificacoes;

import java.time.Duration;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reivindica itens da fila de e-mails com lease, com a mesma mecanica da fila
 * de analise: {@code FOR UPDATE SKIP LOCKED} + {@code RETURNING}, em transacao
 * curta, para que o envio SMTP ocorra fora de transacao de banco.
 */
@Component
public class ReivindicadorDeEmails {

    private static final String SQL_REIVINDICAR = """
            update email_outbox
               set status = 'ENVIANDO',
                   reivindicado_em = now(),
                   tentativas = tentativas + 1
             where id in (select id
                            from email_outbox
                           where status = 'PENDENTE'
                             and disponivel_em <= now()
                           order by disponivel_em, id
                           limit ?
                             for update skip locked)
            returning id
            """;

    private static final String SQL_DEVOLVER_LEASE_EXPIRADO = """
            update email_outbox
               set status = 'PENDENTE',
                   reivindicado_em = null
             where status = 'ENVIANDO'
               and reivindicado_em < now() - make_interval(secs => ?)
            """;

    private final JdbcClient jdbc;

    public ReivindicadorDeEmails(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<Long> reivindicar(int lote) {
        if (lote <= 0) {
            return List.of();
        }
        return jdbc.sql(SQL_REIVINDICAR).param(lote).query(Long.class).list();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int devolverLeasesExpirados(Duration lease) {
        return jdbc.sql(SQL_DEVOLVER_LEASE_EXPIRADO).param(lease.toMillis() / 1000.0).update();
    }
}
