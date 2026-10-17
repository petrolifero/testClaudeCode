package com.gupyclone.notificacoes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gupyclone.candidaturas.Candidatura;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.IllegalTransactionStateException;

/**
 * Prova da transacionalidade do padrao outbox: o aviso nasce e morre junto com
 * a transacao que concluiu a candidatura, e nunca em transacao propria.
 */
class FilaDeEmailsNoBancoTest extends TesteDeNotificacoes {

    @Test
    @DisplayName("enfileirar exige a transacao de quem concluiu a candidatura")
    void semTransacaoNaoEnfileira() {
        Long candidaturaId = gravarCandidatura("carlos", "Pessoa Desenvolvedora Java");
        Candidatura candidatura = candidaturas.findById(candidaturaId).orElseThrow();

        assertThatThrownBy(() -> fila.enfileirar(candidatura, TipoEmail.CANDIDATURA_APROVADA, 90, null))
                .isInstanceOf(IllegalTransactionStateException.class);

        assertThat(emails.count()).isZero();
    }

    @Test
    @DisplayName("se a transacao que enfileirou falhar, o aviso nao fica no banco")
    void avisoNaoSobreviveAoRollback() {
        Long candidaturaId = gravarCandidatura("carlos", "Pessoa Desenvolvedora Java");

        assertThatThrownBy(() -> transacao.executeWithoutResult(status -> {
            Candidatura candidatura = candidaturas.findById(candidaturaId).orElseThrow();
            fila.enfileirar(candidatura, TipoEmail.CANDIDATURA_APROVADA, 90, null);
            throw new IllegalStateException("a conclusao da candidatura falhou depois de enfileirar");
        })).isInstanceOf(IllegalStateException.class);

        // Com transacao propria (REQUIRES_NEW) o aviso teria sobrevivido e o
        // candidato receberia e-mail sobre uma aprovacao que nao aconteceu.
        assertThat(emails.count()).isZero();
    }

    @Test
    @DisplayName("enfileirar duas vezes a mesma (candidatura, tipo) nao cria segunda linha")
    void enfileirarDuasVezesGravaUmaLinha() {
        Long candidaturaId = gravarCandidatura("joao", "Pessoa Desenvolvedora Java");

        enfileirar(candidaturaId, TipoEmail.CANDIDATURA_REPROVADA, 20, "Critérios não atendidos.");
        enfileirar(candidaturaId, TipoEmail.CANDIDATURA_REPROVADA, 20, "Critérios não atendidos.");

        assertThat(emails.countByCandidaturaId(candidaturaId)).isEqualTo(1);
        assertThat(emails.findAllByStatus(StatusEmail.PENDENTE)).hasSize(1);
    }

    @Test
    @DisplayName("tipos diferentes da mesma candidatura sao avisos diferentes")
    void tiposDiferentesConvivem() {
        Long candidaturaId = gravarCandidatura("joao", "Pessoa Desenvolvedora Java");

        enfileirar(candidaturaId, TipoEmail.CANDIDATURA_APROVADA, 80, null);
        enfileirar(candidaturaId, TipoEmail.CANDIDATURA_REPROVADA, 20, "Não atendeu.");

        assertThat(emails.countByCandidaturaId(candidaturaId)).isEqualTo(2);
    }

    @Test
    @DisplayName("candidatura nao gravada nao entra na fila")
    void candidaturaSemIdNaoEntra() {
        assertThatThrownBy(() -> transacao.executeWithoutResult(status ->
                fila.enfileirar(null, TipoEmail.CANDIDATURA_APROVADA, 90, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
