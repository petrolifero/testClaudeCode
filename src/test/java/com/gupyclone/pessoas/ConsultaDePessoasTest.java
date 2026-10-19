package com.gupyclone.pessoas;

import static org.assertj.core.api.Assertions.assertThat;

import com.gupyclone.suporte.TesteDeIntegracao;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Prova da porta de leitura de pessoas sobre as personas semeadas pela migracao V2. */
class ConsultaDePessoasTest extends TesteDeIntegracao {

    @Autowired
    private ConsultaDePessoas consulta;

    @Autowired
    private CandidatoRepository candidatos;

    @Autowired
    private RecrutadorRepository recrutadores;

    @Test
    @DisplayName("devolve os dados do candidato gravado")
    void devolveOsDadosDoCandidato() {
        Long carlos = candidatos.findByChave("carlos").orElseThrow().getId();

        DadosDoCandidato dados = consulta.candidato(carlos).orElseThrow();

        assertThat(dados.id()).isEqualTo(carlos);
        assertThat(dados.chave()).isEqualTo("carlos");
        assertThat(dados.nome()).isEqualTo("Carlos Pereira");
        assertThat(dados.email()).isEqualTo("carlos.pereira@exemplo.com.br");
    }

    @Test
    @DisplayName("devolve os dados do recrutador gravado")
    void devolveOsDadosDoRecrutador() {
        Long ana = recrutadores.findByChave("ana").orElseThrow().getId();

        DadosDoRecrutador dados = consulta.recrutador(ana).orElseThrow();

        assertThat(dados.id()).isEqualTo(ana);
        assertThat(dados.chave()).isEqualTo("ana");
        assertThat(dados.nome()).isEqualTo("Ana Souza");
        assertThat(dados.email()).isEqualTo("ana.souza@gupyclone.com.br");
    }

    @Test
    @DisplayName("cada candidato e lido pelo proprio id")
    void cadaCandidatoPeloProprioId() {
        Long carlos = candidatos.findByChave("carlos").orElseThrow().getId();
        Long joao = candidatos.findByChave("joao").orElseThrow().getId();

        assertThat(carlos).isNotEqualTo(joao);
        assertThat(consulta.candidato(carlos).orElseThrow().nome()).isEqualTo("Carlos Pereira");
        assertThat(consulta.candidato(joao).orElseThrow().nome()).isEqualTo("Joao Lima");
    }

    @Test
    @DisplayName("id inexistente devolve vazio dos dois lados, nao excecao")
    void idInexistenteDevolveVazio() {
        assertThat(consulta.candidato(9_999_999L)).isEmpty();
        assertThat(consulta.recrutador(9_999_999L)).isEmpty();
    }
}
