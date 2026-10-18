package com.gupyclone.triagem;

import java.time.Duration;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reivindica itens da fila de analise com lease.
 *
 * <p>O {@code UPDATE ... WHERE id IN (SELECT ... FOR UPDATE SKIP LOCKED)} e o
 * que torna a fila segura com varios workers: cada linha e entregue a um unico
 * reivindicante e os demais seguem para as proximas linhas em vez de esperar.
 * O {@code RETURNING} devolve exatamente os ids desta reivindicacao, sem
 * confundir com o que outro worker pegou no mesmo instante.
 *
 * <p>Em transacao propria e curta: a analise acontece <em>fora</em> dela.
 */
@Component
public class ReivindicadorDeAnalises {

    private static final String SQL_REIVINDICAR = """
            update analise_job
               set status = 'PROCESSANDO',
                   reivindicado_em = now(),
                   tentativas = tentativas + 1,
                   atualizado_em = now()
             where id in (select id
                            from analise_job
                           where status = 'PENDENTE'
                             and disponivel_em <= now()
                           order by disponivel_em, id
                           limit ?
                             for update skip locked)
            returning id
            """;

    // Um job cujo worker morre sempre no mesmo ponto nao pode voltar para a fila
    // indefinidamente: a cada reivindicacao "tentativas" cresce, e esgotadas as
    // tentativas ele vira FALHO aqui, do mesmo jeito que viraria por
    // registrarFalha. Sem isso, um job envenenado seria reivindicado para
    // sempre e nunca apareceria como problema.
    private static final String SQL_DEVOLVER_LEASE_EXPIRADO = """
            update analise_job
               set status = case when tentativas >= ? then 'FALHO' else 'PENDENTE' end,
                   ultimo_erro = case when tentativas >= ? then
                                     coalesce(ultimo_erro, 'lease expirado sem conclusao')
                                 else ultimo_erro end,
                   reivindicado_em = null,
                   atualizado_em = now()
             where status = 'PROCESSANDO'
               and reivindicado_em < now() - make_interval(secs => ?)
            """;

    /**
     * Devolve a fila os jobs que esgotaram as tentativas tempo suficiente atras.
     *
     * <p>Sem isto, FALHO era terminal: nenhum SQL o selecionava e nenhum
     * agendador o olhava, de modo que uma indisponibilidade de segundos da
     * analise deixava a candidatura em RECEBIDA para sempre, sem decisao e sem
     * e-mail, recuperavel apenas por SQL manual. Isso contradizia a promessa
     * central do projeto.
     */
    private static final String SQL_TIRAR_DA_QUARENTENA = """
            update analise_job
               set status = 'PENDENTE',
                   tentativas = 0,
                   disponivel_em = now(),
                   atualizado_em = now()
             where status = 'FALHO'
               and atualizado_em < now() - make_interval(secs => ?)
            """;

    private final JdbcClient jdbc;

    public ReivindicadorDeAnalises(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * @param lote quantos itens reivindicar no maximo
     * @return ids de {@code analise_job} que passam a pertencer a quem chamou
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<Long> reivindicar(int lote) {
        if (lote <= 0) {
            return List.of();
        }
        return jdbc.sql(SQL_REIVINDICAR).param(lote).query(Long.class).list();
    }

    /**
     * Devolve a fila os jobs cujo worker morreu antes de concluir. E isso que
     * garante que nenhuma candidatura fique presa em PROCESSANDO para sempre.
     *
     * @param maxTentativas esgotadas as tentativas, o job vira FALHO em vez de
     *                      voltar para a fila
     * @return quantos jobs sairam de PROCESSANDO
     */
    /**
     * @param quarentena tempo minimo em FALHO antes de tentar de novo
     * @return quantos jobs voltaram para a fila
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
