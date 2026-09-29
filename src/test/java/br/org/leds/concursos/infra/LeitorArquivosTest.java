package br.org.leds.concursos.infra;

import br.org.leds.concursos.dominio.Candidato;
import br.org.leds.concursos.dominio.Concurso;
import br.org.leds.concursos.dominio.Cpf;
import br.org.leds.concursos.infra.csv.ArquivoInvalidoException;
import br.org.leds.concursos.infra.csv.LeitorArquivos;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LeitorArquivosTest {

    private static final String CABECALHO_CANDIDATOS = "Nome,Data de Nascimento,CPF,Profissões\n";
    private static final String CABECALHO_CONCURSOS = "Órgão,Edital,Código do Concurso,Lista de vagas\n";

    private final LeitorArquivos leitor = new LeitorArquivos();

    @Test
    void leCandidatosComListasEntreAspasOuNao() {
        String csv = CABECALHO_CANDIDATOS
                + "Ana Sophia Araújo,18/12/2004,438.150.926-98,\"[marceneiro, carpinteiro, eletricista]\"\n"
                + "Antônio Carvalho,19/05/1969,380.416.975-93,[engenheiro civil]\n";

        List<Candidato> candidatos = leitor.lerCandidatos(new StringReader(csv), "candidatos.txt");

        assertThat(candidatos).hasSize(2);
        Candidato ana = candidatos.getFirst();
        assertThat(ana.nome()).isEqualTo("Ana Sophia Araújo");
        assertThat(ana.dataNascimento()).isEqualTo(LocalDate.of(2004, 12, 18));
        assertThat(ana.cpf()).isEqualTo(Cpf.of("438.150.926-98"));
        assertThat(ana.profissoes()).containsExactlyInAnyOrder("marceneiro", "carpinteiro", "eletricista");
        assertThat(candidatos.get(1).profissoes()).containsExactly("engenheiro civil");
    }

    @Test
    void leConcursos() {
        String csv = CABECALHO_CONCURSOS + "PMES,17/2016,55312084049,\"[engenheiro civil,encanador,arquiteto]\"\n";

        List<Concurso> concursos = leitor.lerConcursos(new StringReader(csv), "concursos.txt");

        assertThat(concursos).singleElement().satisfies(c -> {
            assertThat(c.orgao()).isEqualTo("PMES");
            assertThat(c.edital()).isEqualTo("17/2016");
            assertThat(c.codigo().valor()).isEqualTo("55312084049");
            assertThat(c.vagas()).containsExactlyInAnyOrder("engenheiro civil", "encanador", "arquiteto");
        });
    }

    @Test
    void informaArquivoELinhaQuandoDataInvalida() {
        String csv = CABECALHO_CANDIDATOS
                + "Ana,18/12/2004,438.150.926-98,[marceneiro]\n"
                + "Beto,31/02/2004,380.416.975-93,[marceneiro]\n";

        assertThatThrownBy(() -> leitor.lerCandidatos(new StringReader(csv), "candidatos.txt"))
                .isInstanceOf(ArquivoInvalidoException.class)
                .hasMessageStartingWith("candidatos.txt, linha 3");
    }

    @Test
    void rejeitaCpfInvalido() {
        String csv = CABECALHO_CANDIDATOS + "Ana,18/12/2004,438.150.926-00,[marceneiro]\n";

        assertThatThrownBy(() -> leitor.lerCandidatos(new StringReader(csv), "candidatos.txt"))
                .isInstanceOf(ArquivoInvalidoException.class)
                .hasMessageContaining("linha 2")
                .hasMessageContaining("CPF");
    }

    @Test
    void rejeitaCpfDuplicado() {
        String csv = CABECALHO_CANDIDATOS
                + "Ana,18/12/2004,438.150.926-98,[marceneiro]\n"
                + "Ana de Novo,18/12/2004,43815092698,[pintor]\n";

        assertThatThrownBy(() -> leitor.lerCandidatos(new StringReader(csv), "candidatos.txt"))
                .isInstanceOf(ArquivoInvalidoException.class)
                .hasMessageContaining("CPF duplicado");
    }

    @Test
    void rejeitaQuantidadeErradaDeColunas() {
        String csv = CABECALHO_CONCURSOS + "PMES,17/2016,55312084049\n";

        assertThatThrownBy(() -> leitor.lerConcursos(new StringReader(csv), "concursos.txt"))
                .isInstanceOf(ArquivoInvalidoException.class)
                .hasMessageContaining("esperadas 4 colunas");
    }

    @Test
    void leOsArquivosOficiaisDoDesafio() throws Exception {
        try (Reader candidatos = abrir("/dados/candidatos.txt"); Reader concursos = abrir("/dados/concursos.txt")) {
            assertThat(leitor.lerCandidatos(candidatos, "candidatos.txt")).hasSize(1000);
            assertThat(leitor.lerConcursos(concursos, "concursos.txt")).hasSize(1000);
        }
    }

    private Reader abrir(String caminho) {
        return new InputStreamReader(
                Objects.requireNonNull(getClass().getResourceAsStream(caminho)), StandardCharsets.UTF_8);
    }
}
