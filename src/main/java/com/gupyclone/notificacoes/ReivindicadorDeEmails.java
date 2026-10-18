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

    // Mesmo cuidado da fila de analise: esgotadas as tentativas o aviso vira
    // FALHO em vez de ser reivindicado para sempre por um worker que morre
    // sempre no mesmo ponto.
    private static final String SQL_DEVOLVER_LEASE_EXPIRADO = """
            update email_outbox
               set status = case when tentativas >= ? then 'FALHO' else 'PENDENTE' end,
                   ultimo_erro = case when tentativas >= ? then
                                     coalesce(ultimo_erro, 'lease expirado sem conclusao')
                                 else ultimo_erro end,
                   reivindicado_em = null
             where status = 'ENVIANDO'
               and reivindicado_em < now() - make_interval(secs => ?)
            """;

    /** Mesma quarentena da fila de analise: FALHO e descanso, nao tumulo. */
    private static final String SQL_TIRAR_DA_QUARENTENA = """
            update email_outbox
               set status = 'PENDENTE',
                   tentativas = 0,
                   disponivel_em = now()
             where status = 'FALHO'
               and coalesce(reivindicado_em, criado_em) < now() - make_interval(secs => ?)
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

    /**
     * @param maxTentativas esgotadas as tentativas, o aviso vira FALHO em vez
     *                      de voltar para a fila
     * @return quantos avisos sairam de ENVIANDO
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int tirarDaQuarentena(Duration quarentena) {
        return jdbc.sql(SQL_TIRAR_DA_QUARENTENA).param(quarentena.toMillis() / 1000.0).update();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int devolverLeasesExpirados(Duration lease, int maxTentativas) {
        return jdbc.sql(SQL_DEVOLVER_LEASE_EXPIRADO)
                .param(maxTentativas)
                .param(maxTentativas)
                .param(lease.toMillis() / 1000.0)
                .update();
    }
}
