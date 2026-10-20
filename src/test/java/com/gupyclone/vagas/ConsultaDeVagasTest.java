package com.gupyclone.vagas;

import static org.assertj.core.api.Assertions.assertThat;

import com.gupyclone.pessoas.RecrutadorRepository;
import com.gupyclone.suporte.TesteDeIntegracao;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Prova da porta de leitura de vagas contra o PostgreSQL real. */
class ConsultaDeVagasTest extends TesteDeIntegracao {

    @Autowired
    private ConsultaDeVagas consulta;

    @Autowired
    private ServicoDeVagas servico;

    @Autowired
    private RecrutadorRepository recrutadores;

    private Long ana;

    @BeforeEach
    void localizarRecrutadora() {
        ana = recrutadores.findByChave("ana").orElseThrow().getId();
    }

    @Test
    @DisplayName("devolve os dados da vaga gravada, com o id do recrutador")
    void devolveOsDadosDaVaga() {
        Vaga publicada = servico.publicar(ana, "Pessoa Desenvolvedora Java",
                "Back-end com Spring Boot e PostgreSQL", 60,
                List.of(new ServicoDeVagas.NovoCriterio("Java", "java", 50, true)));

        DadosDaVaga dados = consulta.porId(publicada.getId()).orElseThrow();

        assertThat(dados.id()).isEqualTo(publicada.getId());
        assertThat(dados.titulo()).isEqualTo("Pessoa Desenvolvedora Java");
        assertThat(dados.descricao()).isEqualTo("Back-end com Spring Boot e PostgreSQL");
        assertThat(dados.status()).isEqualTo(StatusVaga.ABERTA);
        assertThat(dados.pontuacaoMinima()).isEqualTo(60);
        assertThat(dados.recrutadorId()).isEqualTo(ana);
        assertThat(dados.estaAberta()).isTrue();
    }

    @Test
    @DisplayName("estaAberta acompanha o status gravado: vaga fechada nao esta aberta")
    void estaAbertaAcompanhaOStatus() {
        Long vaga = servico.publicar(ana, "Pessoa de Dados", "Pipelines de dados", 40,
                List.of(new ServicoDeVagas.NovoCriterio("SQL", "sql", 40, false))).getId();

        servico.fechar(vaga, ana);

        DadosDaVaga dados = consulta.porId(vaga).orElseThrow();
        assertThat(dados.status()).isEqualTo(StatusVaga.FECHADA);
        assertThat(dados.estaAberta()).isFalse();
    }

    @Test
    @DisplayName("id inexistente devolve vazio, nao excecao")
    void idInexistenteDevolveVazio() {
        assertThat(consulta.porId(9_999_999L)).isEmpty();
    }
}
