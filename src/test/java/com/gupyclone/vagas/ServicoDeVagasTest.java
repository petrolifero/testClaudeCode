package com.gupyclone.vagas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gupyclone.pessoas.ConsultaDePessoas;
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

    @Autowired
    private ConsultaDePessoas pessoas;

    private Long ana;
    private Long bia;

    @BeforeEach
    void localizarRecrutadoras() {
        ana = recrutadores.findByChave("ana").orElseThrow().getId();
        // O limpador devolve as pessoas ao estado da migracao V2, entao esta
        // segunda recrutadora e recriada a cada teste; o orElseGet cobre o caso
        // de ela ainda existir, em que recriar violaria o unique de chave.
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

        // Sem transacao em volta do teste: a vaga chega destacada, e ler os
        // criterios aqui so funciona se o servico os trouxe no mesmo SELECT.
        assertThat(servico.porId(vaga).getCriterios()).hasSize(2);
        assertThat(servico.abertas().getFirst().getCriterios()).hasSize(2);
        assertThat(servico.doRecrutador(ana).getFirst().getCriterios()).hasSize(2);

        // O recrutador deixou de ser associacao e passou a ser id: continua
        // afirmado quem publicou, agora pela porta do modulo dono.
        assertThat(servico.porId(vaga).getRecrutadorId()).isEqualTo(ana);
        assertThat(servico.abertas().getFirst().getRecrutadorId()).isEqualTo(ana);
        assertThat(pessoas.recrutador(ana).orElseThrow().nome()).isEqualTo("Ana Souza");
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

    @Test
    @DisplayName("expressao que nao procura nada e recusada na publicacao")
    void expressaoInutilERecusada() {
        // "-", "ou", "com": websearch_to_tsquery reduz tudo isso a uma tsquery
        // VAZIA, que nao casa com curriculo nenhum. Se o criterio fosse
        // obrigatorio, reprovaria todo candidato apontando algo que ninguem
        // poderia cumprir, e nada avisava o RH.
        // Espaco em branco nao entra aqui: ele e recusado antes, pela
        // validacao de campo obrigatorio, com mensagem mais especifica.
        for (String inutil : new String[]{"-", "ou", "com", "de", "!!!"}) {
            assertThatThrownBy(() -> servico.publicar(ana, "Pessoa de Backend", "Descrição", 50,
                    List.of(new ServicoDeVagas.NovoCriterio("Experiência com Java", inutil, 100, true))))
                    .as("expressão %s", inutil)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("não procura nada");
        }
    }

    @Test
    @DisplayName("expressao com termo de verdade continua aceita")
    void expressaoUtilEAceita() {
        var vaga = servico.publicar(ana, "Pessoa de Backend", "Descrição", 50,
                List.of(new ServicoDeVagas.NovoCriterio("Java", "java or jvm", 100, true),
                        new ServicoDeVagas.NovoCriterio("Sem PHP", "-php", 10, false)));

        assertThat(vaga.getCriterios()).hasSize(2);
    }
}
