package com.gupyclone.curriculos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gupyclone.pessoas.CandidatoRepository;
import com.gupyclone.suporte.FabricaDePdf;
import com.gupyclone.suporte.TesteDeIntegracao;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Prova do recebimento de curriculo de ponta a ponta: PostgreSQL real, PDFs
 * reais e OCR real, com o texto gravado de forma que a busca textual da
 * triagem consiga encontra-lo.
 */
class ServicoDeCurriculosTest extends TesteDeIntegracao {

    @Autowired
    private RecebedorDeCurriculos recebedor;

    @Autowired
    private CurriculoRepository curriculos;

    @Autowired
    private CandidatoRepository candidatos;

    @Autowired
    private JdbcClient jdbc;

    private Long carlos;

    @BeforeEach
    void localizarCandidato() {
        carlos = candidatos.findByChave("carlos").orElseThrow().getId();
    }

    @Test
    @DisplayName("guarda o curriculo com camada de texto e registra a origem")
    void guardaCurriculoComCamadaDeTexto() {
        byte[] pdf = FabricaDePdf.comCamadaDeTexto(
                "Carlos Pereira",
                "Desenvolvedor Java com Spring Boot",
                "Experiencia com PostgreSQL e Docker");

        Curriculo salvo = recebedor.receber(carlos, "carlos-pereira.pdf", pdf);

        assertThat(salvo.getId()).isNotNull();
        var doBanco = curriculos.findAllByCandidatoIdOrderByCriadoEmDesc(carlos);
        assertThat(doBanco).hasSize(1);
        Curriculo recuperado = doBanco.getFirst();
        assertThat(recuperado.getId()).isEqualTo(salvo.getId());
        assertThat(recuperado.getNomeArquivo()).isEqualTo("carlos-pereira.pdf");
        assertThat(recuperado.getOrigemTexto()).isEqualTo(OrigemTexto.CAMADA_DE_TEXTO);
        assertThat(recuperado.getPaginas()).isEqualTo(1);
        assertThat(recuperado.getTexto()).contains("Spring Boot");
        assertThat(recuperado.getConteudoPdf()).isEqualTo(pdf);
        assertThat(recuperado.getCriadoEm()).isNotNull();
    }

    @Test
    @DisplayName("guarda o curriculo que so existe como imagem, com origem OCR")
    void guardaCurriculoSomenteImagem() {
        byte[] pdf = FabricaDePdf.somenteImagem(
                "Joao Lima",
                "Desenvolvedor Java com Spring Boot",
                "Experiencia com PostgreSQL");
        Long joao = candidatos.findByChave("joao").orElseThrow().getId();

        Curriculo salvo = recebedor.receber(joao, "joao-digitalizado.pdf", pdf);

        Curriculo recuperado = curriculos.findById(salvo.getId()).orElseThrow();
        assertThat(recuperado.getOrigemTexto()).isEqualTo(OrigemTexto.OCR);
        assertThat(recuperado.getTexto().toLowerCase()).contains("java");
    }

    @Test
    @DisplayName("o texto gravado alimenta a busca textual que a triagem usa")
    void textoGravadoEhPesquisavel() {
        recebedor.receber(carlos, "carlos.pdf", FabricaDePdf.comCamadaDeTexto(
                "Carlos Pereira",
                "Desenvolvedor Java com Spring Boot e PostgreSQL"));

        Integer encontrados = jdbc.sql("""
                select count(*)
                  from curriculo
                 where texto_busca @@ websearch_to_tsquery('portugues_sem_acento', ?)
                """).param("java").query(Integer.class).single();

        assertThat(encontrados).isEqualTo(1);
    }

    @Test
    @DisplayName("dois envios do mesmo candidato convivem, do mais novo para o mais antigo")
    void doisEnviosDoMesmoCandidato() {
        recebedor.receber(carlos, "antigo.pdf", FabricaDePdf.comCamadaDeTexto(
                "Carlos Pereira", "Analista de sistemas com foco em COBOL e DB2"));
        recebedor.receber(carlos, "novo.pdf", FabricaDePdf.comCamadaDeTexto(
                "Carlos Pereira", "Desenvolvedor Java com Spring Boot e PostgreSQL"));

        var doBanco = curriculos.findAllByCandidatoIdOrderByCriadoEmDesc(carlos);

        assertThat(doBanco).hasSize(2);
        assertThat(doBanco).extracting(Curriculo::getNomeArquivo).containsExactly("novo.pdf", "antigo.pdf");
    }

    @Test
    @DisplayName("recusa arquivo vazio, arquivo que nao e PDF e PDF sem texto")
    void recusaArquivoInvalido() {
        assertThatThrownBy(() -> recebedor.receber(carlos, "vazio.pdf", new byte[0]))
                .isInstanceOf(CurriculoInvalidoException.class);
        assertThatThrownBy(() -> recebedor.receber(carlos, "nulo.pdf", null))
                .isInstanceOf(CurriculoInvalidoException.class);
        assertThatThrownBy(() -> recebedor.receber(
                carlos, "texto.pdf", "nao sou um pdf".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(CurriculoInvalidoException.class);
        assertThatThrownBy(() -> recebedor.receber(carlos, "branco.pdf", FabricaDePdf.paginaEmBranco()))
                .isInstanceOf(CurriculoInvalidoException.class);

        assertThat(curriculos.count()).isZero();
    }

    @Test
    @DisplayName("recusa envio sem nome de arquivo")
    void recusaSemNomeDeArquivo() {
        byte[] pdf = FabricaDePdf.comCamadaDeTexto(
                "Carlos Pereira", "Desenvolvedor Java com Spring Boot e PostgreSQL");

        assertThatThrownBy(() -> recebedor.receber(carlos, "   ", pdf))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> recebedor.receber(carlos, null, pdf))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> recebedor.receber(carlos, "n".repeat(256) + ".pdf", pdf))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(curriculos.count()).isZero();
    }

    @Test
    @DisplayName("recusa candidato inexistente sem gravar nada")
    void recusaCandidatoInexistente() {
        byte[] pdf = FabricaDePdf.comCamadaDeTexto(
                "Fantasma", "Desenvolvedor Java com Spring Boot e PostgreSQL");

        assertThatThrownBy(() -> recebedor.receber(9_999_999L, "fantasma.pdf", pdf))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("9999999");

        assertThat(curriculos.count()).isZero();
    }
}
