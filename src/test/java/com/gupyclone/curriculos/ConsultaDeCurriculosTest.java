package com.gupyclone.curriculos;

import static org.assertj.core.api.Assertions.assertThat;

import com.gupyclone.pessoas.CandidatoRepository;
import com.gupyclone.suporte.FabricaDePdf;
import com.gupyclone.suporte.TesteDeIntegracao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Prova da porta de leitura de curriculos, com PDF de verdade no PostgreSQL real. */
class ConsultaDeCurriculosTest extends TesteDeIntegracao {

    @Autowired
    private ConsultaDeCurriculos consulta;

    @Autowired
    private RecebedorDeCurriculos recebedor;

    @Autowired
    private CandidatoRepository candidatos;

    private Long carlos;

    @BeforeEach
    void localizarCandidato() {
        carlos = candidatos.findByChave("carlos").orElseThrow().getId();
    }

    @Test
    @DisplayName("devolve os dados do curriculo gravado, com o id do candidato")
    void devolveOsDadosDoCurriculo() {
        Curriculo salvo = recebedor.receber(carlos, "carlos-pereira.pdf",
                FabricaDePdf.comCamadaDeTexto("Carlos Pereira",
                        "Desenvolvedor Java com Spring Boot"));

        DadosDoCurriculo dados = consulta.porId(salvo.getId()).orElseThrow();

        assertThat(dados.id()).isEqualTo(salvo.getId());
        assertThat(dados.candidatoId()).isEqualTo(carlos);
        assertThat(dados.nomeArquivo()).isEqualTo("carlos-pereira.pdf");
        assertThat(dados.origemTexto()).isEqualTo(OrigemTexto.CAMADA_DE_TEXTO);
        assertThat(dados.paginas()).isEqualTo(1);
    }

    @Test
    @DisplayName("id inexistente devolve vazio, nao excecao")
    void idInexistenteDevolveVazio() {
        assertThat(consulta.porId(9_999_999L)).isEmpty();
    }
}
