package br.leds.comum.cli;

import br.leds.comum.aplicacao.ServicoConsultas;
import br.leds.comum.dominio.CandidatoEncontrado;
import br.leds.comum.dominio.Cpf;
import java.io.PrintStream;
import java.time.format.DateTimeFormatter;
import java.util.List;

final class CandidatosPorConcursoComando implements Comando {

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/uuuu");

    private final ServicoConsultas consultas;

    CandidatosPorConcursoComando(ServicoConsultas consultas) {
        this.consultas = consultas;
    }

    @Override
    public String nome() {
        return "candidatos-por-concurso";
    }

    @Override
    public String uso() {
        return "candidatos-por-concurso <codigo>";
    }

    @Override
    public String descricao() {
        return "candidatos compatíveis com o concurso";
    }

    @Override
    public int quantidadeArgumentos() {
        return 1;
    }

    @Override
    public void executar(List<String> argumentos, PrintStream saida) {
        long inicio = System.nanoTime();
        List<CandidatoEncontrado> lista = consultas.candidatosPorConcurso(argumentos.get(0));
        long tempo = Cronometro.milissegundos(inicio);
        lista.forEach(c -> saida.printf("%s | %s | %s%n", c.nome(), DATA.format(c.dataNascimento()), Cpf.formatar(c.cpf())));
        saida.printf("-- %d candidato(s) em %d ms%n", lista.size(), tempo);
    }
}
