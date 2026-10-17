package com.gupyclone.triagem;

import static org.assertj.core.api.Assertions.assertThat;

import com.gupyclone.config.PropriedadesTriagem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Um job cujo worker morre sempre antes de concluir nao pode ser reivindicado
 * para sempre.
 *
 * <p>A recuperacao de lease devolve o job a fila e cada reivindicacao
 * incrementa as tentativas. Esgotadas as tentativas, a recuperacao promove o
 * job a FALHO: sem isso um job envenenado circularia indefinidamente, com as
 * tentativas crescendo, sem nunca aparecer como problema.
 */
class LeaseEnvenenadoTest extends TesteDeTriagem {

    @Autowired
    private ReivindicadorDeAnalises reivindicador;

    @Autowired
    private PropriedadesTriagem propriedades;

    @Test
    @DisplayName("lease expirado com tentativas de sobra volta para a fila")
    void comTentativasDeSobraVoltaParaAFila() {
        Long jobId = jobAbandonado(1);

        assertThat(reivindicador.devolverLeasesExpirados(propriedades.lease(), propriedades.maxTentativas()))
                .isEqualTo(1);

        AnaliseJob job = analises.findById(jobId).orElseThrow();
        assertThat(job.getStatus()).isEqualTo(StatusJob.PENDENTE);
        assertThat(job.getReivindicadoEm()).isNull();
    }

    @Test
    @DisplayName("lease expirado com as tentativas esgotadas vira FALHO em vez de circular")
    void comTentativasEsgotadasViraFalho() {
        Long jobId = jobAbandonado(propriedades.maxTentativas());

        assertThat(reivindicador.devolverLeasesExpirados(propriedades.lease(), propriedades.maxTentativas()))
                .isEqualTo(1);

        AnaliseJob job = analises.findById(jobId).orElseThrow();
        assertThat(job.getStatus())
                .as("job envenenado para de circular e fica visivel como falha")
                .isEqualTo(StatusJob.FALHO);
        assertThat(job.getUltimoErro()).isNotBlank();
        assertThat(reivindicador.reivindicar(10))
                .as("FALHO nao e mais reivindicado")
                .isEmpty();
    }

    @Test
    @DisplayName("lease ainda valido nao e tocado")
    void leaseValidoNaoEMexido() {
        Long jobId = jobReivindicadoAgora();

        assertThat(reivindicador.devolverLeasesExpirados(propriedades.lease(), propriedades.maxTentativas()))
                .isZero();
        assertThat(analises.findById(jobId).orElseThrow().getStatus()).isEqualTo(StatusJob.PROCESSANDO);
    }

    /** Candidatura real cujo job ficou preso em PROCESSANDO com lease vencido. */
    private Long jobAbandonado(int tentativas) {
        Long jobId = jobDeUmaCandidaturaReal();
        jdbc.sql("""
                update analise_job
                   set status = 'PROCESSANDO',
                       reivindicado_em = now() - interval '1 hour',
                       tentativas = ?
                 where id = ?
                """).param(tentativas).param(jobId).update();
        return jobId;
    }

    private Long jobReivindicadoAgora() {
        Long jobId = jobDeUmaCandidaturaReal();
        jdbc.sql("""
                update analise_job
                   set status = 'PROCESSANDO',
                       reivindicado_em = now(),
                       tentativas = 1
                 where id = ?
                """).param(jobId).update();
        return jobId;
    }

    private Long jobDeUmaCandidaturaReal() {
        var vaga = publicarVagaDeExemplo(50);
        var candidatura = candidatar(carlos, vaga.getId(), PDF_QUE_ATENDE);
        return analises.findByCandidaturaId(candidatura.getId()).orElseThrow().getId();
    }
}
