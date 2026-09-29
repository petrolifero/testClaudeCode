package br.leds.comum.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.leds.comum.aplicacao.CargaDados;
import br.leds.comum.aplicacao.ConsultasConcursos;
import br.leds.comum.aplicacao.ServicoConsultas;
import br.leds.comum.aplicacao.ServicoImportacao;
import br.leds.comum.dominio.Candidato;
import br.leds.comum.dominio.CandidatoEncontrado;
import br.leds.comum.dominio.Concurso;
import br.leds.comum.dominio.ConcursoEncontrado;
import br.leds.comum.erro.ErroInfraestrutura;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class CliTest {

    private final ByteArrayOutputStream bufferSaida = new ByteArrayOutputStream();
    private final ByteArrayOutputStream bufferErro = new ByteArrayOutputStream();
    private final PrintStream saida = new PrintStream(bufferSaida, true, StandardCharsets.UTF_8);
    private final PrintStream erro = new PrintStream(bufferErro, true, StandardCharsets.UTF_8);

    private Cli cli(ConsultasConcursos consultas) {
        CargaDados carga = new CargaDados() {
            @Override
            public void criarEsquema() {
            }

            @Override
            public void substituirDados(List<Candidato> candidatos, List<Concurso> concursos) {
            }
        };
        return Cli.padrao(new ServicoConsultas(consultas), new ServicoImportacao(carga));
    }

    private static ConsultasConcursos fixas() {
        return new ConsultasConcursos() {
            @Override
            public List<ConcursoEncontrado> concursosPorCpf(String cpf) {
                return List.of(new ConcursoEncontrado("DER", "4/2017", 19750532858L));
            }

            @Override
            public List<CandidatoEncontrado> candidatosPorConcurso(long codigo) {
                return List.of(new CandidatoEncontrado("Ana Sophia Araújo", LocalDate.of(2004, 12, 18), "43815092698"));
            }
        };
    }

    @Test
    void imprimeConcursosDoCpf() {
        int codigo = cli(fixas()).executar(new String[] {"concursos-por-cpf", "438.150.926-98"}, saida, erro);
        assertEquals(0, codigo);
        String texto = bufferSaida.toString(StandardCharsets.UTF_8);
        assertTrue(texto.contains("DER | edital 4/2017 | código 19750532858"), texto);
        assertTrue(texto.contains("-- 1 concurso(s)"), texto);
    }

    @Test
    void imprimeCandidatosFormatados() {
        int codigo = cli(fixas()).executar(new String[] {"candidatos-por-concurso", "19750532858"}, saida, erro);
        assertEquals(0, codigo);
        assertTrue(bufferSaida.toString(StandardCharsets.UTF_8)
                .contains("Ana Sophia Araújo | 18/12/2004 | 438.150.926-98"));
    }

    @Test
    void entradaInvalidaViraMensagemECodigoUm() {
        int codigo = cli(fixas()).executar(new String[] {"concursos-por-cpf", "123"}, saida, erro);
        assertEquals(1, codigo);
        assertTrue(bufferErro.toString(StandardCharsets.UTF_8).startsWith("Erro: CPF inválido"));
    }

    @Test
    void falhaDeInfraestruturaTambemViraCodigoUm() {
        ConsultasConcursos quebrado = new ConsultasConcursos() {
            @Override
            public List<ConcursoEncontrado> concursosPorCpf(String cpf) {
                throw new ErroInfraestrutura("banco fora do ar");
            }

            @Override
            public List<CandidatoEncontrado> candidatosPorConcurso(long codigo) {
                throw new ErroInfraestrutura("banco fora do ar");
            }
        };
        assertEquals(1, cli(quebrado).executar(new String[] {"concursos-por-cpf", "43815092698"}, saida, erro));
        assertTrue(bufferErro.toString(StandardCharsets.UTF_8).contains("banco fora do ar"));
    }

    @Test
    void argumentosErradosMostramUso() {
        assertEquals(1, cli(fixas()).executar(new String[] {"concursos-por-cpf"}, saida, erro));
        assertTrue(bufferErro.toString(StandardCharsets.UTF_8).contains("Uso: concursos-por-cpf <cpf>"));
    }

    @Test
    void comandoDesconhecidoEListaAjuda() {
        assertEquals(1, cli(fixas()).executar(new String[] {"xpto"}, saida, erro));
        assertTrue(bufferErro.toString(StandardCharsets.UTF_8).contains("importar <candidatos.csv> <concursos.csv>"));
    }

    @Test
    void semArgumentosMostraAjuda() {
        assertEquals(1, cli(fixas()).executar(new String[] {}, saida, erro));
        assertTrue(bufferErro.toString(StandardCharsets.UTF_8).contains("gerar <candidatos> <concursos> <pasta>"));
    }
}
