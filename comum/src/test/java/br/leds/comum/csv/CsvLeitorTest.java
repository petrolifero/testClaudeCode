package br.leds.comum.csv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.leds.comum.dominio.Candidato;
import br.leds.comum.dominio.Concurso;
import br.leds.comum.erro.ErroAplicacao;
import br.leds.comum.erro.EntradaInvalida;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CsvLeitorTest {

    @TempDir
    Path pasta;

    @Test
    void dividePreservandoVirgulasEntreAspas() {
        assertEquals(List.of("a", "[x, y]", "c"), CsvLeitor.dividir("a,\"[x, y]\",c"));
    }

    @Test
    void listaNormalizaEspacosCaixaERepeticoes() {
        assertEquals(List.of("pintor", "arquiteto"), CsvLeitor.lista("[ Pintor, arquiteto ,PINTOR,]"));
    }

    @Test
    void listaSemColchetesEhErro() {
        assertThrows(ErroAplicacao.class, () -> CsvLeitor.lista("pintor"));
    }

    @Test
    void leCandidatos() throws Exception {
        Path arquivo = pasta.resolve("c.csv");
        Files.writeString(arquivo, "Nome,Data,CPF,Prof\nAna Sophia Araújo,18/12/2004,438.150.926-98,\"[marceneiro, carpinteiro]\"\n");
        Candidato c = CsvLeitor.lerCandidatos(arquivo).get(0);
        assertEquals("Ana Sophia Araújo", c.nome());
        assertEquals(LocalDate.of(2004, 12, 18), c.dataNascimento());
        assertEquals("43815092698", c.cpf());
        assertEquals(List.of("marceneiro", "carpinteiro"), c.profissoes());
    }

    @Test
    void leConcursos() throws Exception {
        Path arquivo = pasta.resolve("k.csv");
        Files.writeString(arquivo, "O,E,C,V\nDER,4/2017,19750532858,[pintor]\n");
        Concurso c = CsvLeitor.lerConcursos(arquivo).get(0);
        assertEquals(new Concurso("DER", "4/2017", 19750532858L, List.of("pintor")), c);
    }

    @Test
    void erroIndicaArquivoELinha() throws Exception {
        Path arquivo = pasta.resolve("ruim.csv");
        Files.writeString(arquivo, "O,E,C,V\nDER,4/2017,abc,[pintor]\n");
        ErroAplicacao e = assertThrows(ErroAplicacao.class, () -> CsvLeitor.lerConcursos(arquivo));
        assertEquals(true, e.getMessage().contains("ruim.csv:2"), e.getMessage());
    }

    @Test
    void dataInvalidaEhRejeitada() throws Exception {
        Path arquivo = pasta.resolve("d.csv");
        Files.writeString(arquivo, "N,D,C,P\nAna,31/02/2000,438.150.926-98,[a]\n");
        assertThrows(ErroAplicacao.class, () -> CsvLeitor.lerCandidatos(arquivo));
    }

    @Test
    void arquivoInexistenteEhErroAmigavel() {
        assertThrows(ErroAplicacao.class, () -> CsvLeitor.lerCandidatos(pasta.resolve("nao-existe.csv")));
    }
}
