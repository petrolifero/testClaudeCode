package com.gupyclone.vagas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gupyclone.pessoas.Recrutador;
import com.gupyclone.pessoas.RecrutadorRepository;
import com.gupyclone.suporte.TesteDeIntegracao;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;

/** Prova das regras de vaga que nao passam pelo HTTP, com dois recrutadores de verdade. */
class ServicoDeVagasTest extends TesteDeIntegracao {

    @Autowired
    private ServicoDeVagas servico;

    @Autowired
    private RecrutadorRepository recrutadores;

    private Long ana;
    private Long bia;

    @BeforeEach
    void localizarRecrutadoras() {
        ana = recrutadores.findByChave("ana").orElseThrow().getId();
        // O limpador de banco preserva as pessoas semeadas, e esta segunda
        // recrutadora sobrevive junto: por isso ela e reaproveitada quando ja
        // existe, em vez de recriada (chave e e-mail sao unicos).
        bia = recrutadores.findByChave("bia")
                .orElseGet(() -> recrutadores.save(
                        new Recrutador("bia", "Bia Martins", "bia.martins@gupyclone.com.br")))
                .getId();
    }

    @Test
    @DisplayName("so o RH dono da vaga pode fechar")
    void somenteODonoFecha() {
        Long vaga = publicarPor(ana, "Pessoa Desenvolvedora Java");

        assertThatThrownBy(() -> servico.fechar(vaga, bia))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(servico.porId(vaga).estaAberta()).isTrue();

        servico.fechar(vaga, ana);

        assertThat(servico.porId(vaga).getStatus()).isEqualTo(StatusVaga.FECHADA);
    }

    @Test
    @DisplayName("fechar vaga inexistente da 404 de dominio")
    void fecharVagaInexistente() {
        assertThatThrownBy(() -> servico.fechar(9_999_999L, ana))
                .isInstanceOf(VagaInexistenteException.class);
    }

    @Test
    @DisplayName("buscar vaga inexistente da 404 de dominio")
    void buscarVagaInexistente() {
        assertThatThrownBy(() -> servico.porId(9_999_999L))
                .isInstanceOf(VagaInexistenteException.class);
    }

    @Test
    @DisplayName("cada recrutador ve somente as proprias vagas, abertas ou fechadas")
    void cadaRecrutadorVeAsProprias() {
        Long daAna = publicarPor(ana, "Pessoa Desenvolvedora Java");
        Long daBia = publicarPor(bia, "Analista de dados");
        servico.fechar(daBia, bia);

        assertThat(servico.doRecrutador(ana)).extracting(Vaga::getId).containsExactly(daAna);
        assertThat(servico.doRecrutador(bia)).extracting(Vaga::getId).containsExactly(daBia);
        assertThat(servico.abertas()).extracting(Vaga::getId).containsExactly(daAna);
    }

    @Test
    @DisplayName("as vagas devolvidas podem ser lidas fora da transacao do servico")
    void vagasChegamComAssociacoesCarregadas() {
        Long vaga = publicarPor(ana, "Pessoa Desenvolvedora Java");

        // Sem transacao em volta do teste: a vaga chega destacada, e ler
        // recrutador e criterios aqui so funciona se o servico os carregou.
        assertThat(servico.porId(vaga).getRecrutador().getNome()).isEqualTo("Ana Souza");
        assertThat(servico.porId(vaga).getCriterios()).hasSize(2);
        assertThat(servico.abertas().getFirst().getRecrutador().getNome()).isEqualTo("Ana Souza");
        assertThat(servico.abertas().getFirst().getCriterios()).hasSize(2);
        assertThat(servico.doRecrutador(ana).getFirst().getCriterios()).hasSize(2);
    }

    @Test
    @DisplayName("recusa publicar em nome de recrutador inexistente, sem gravar nada")
    void recusaRecrutadorInexistente() {
        assertThatThrownBy(() -> publicarPor(9_999_999L, "Vaga fantasma"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("9999999");

        assertThat(servico.abertas()).isEmpty();
    }

    @Test
    @DisplayName("recusa vaga sem titulo e sem descricao")
    void recusaTextoEmBranco() {
        assertThatThrownBy(() -> servico.publicar(ana, "  ", "descricao", 50, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> servico.publicar(ana, "titulo", "  ", 50, List.of()))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(servico.abertas()).isEmpty();
    }

    private Long publicarPor(Long recrutadorId, String titulo) {
        return servico.publicar(recrutadorId, titulo, "Descricao de " + titulo, 60,
                List.of(new ServicoDeVagas.NovoCriterio("Java", "java", 50, true),
                        new ServicoDeVagas.NovoCriterio("PostgreSQL", "postgresql or postgres", 30, false)))
                .getId();
    }
}
