package br.leds.comum.cli;

import br.leds.comum.aplicacao.ServicoConsultas;
import br.leds.comum.dominio.ConcursoEncontrado;
import java.io.PrintStream;
import java.util.List;

final class ConcursosPorCpfComando implements Comando {

    private final ServicoConsultas consultas;

    ConcursosPorCpfComando(ServicoConsultas consultas) {
        this.consultas = consultas;
    }

    @Override
    public String nome() {
        return "concursos-por-cpf";
    }

    @Override
    public String uso() {
        return "concursos-por-cpf <cpf>";
    }

    @Override
    public String descricao() {
        return "concursos compatíveis com o candidato";
    }

    @Override
    public int quantidadeArgumentos() {
        return 1;
    }

    @Override
    public void executar(List<String> argumentos, PrintStream saida) {
        long inicio = System.nanoTime();
        List<ConcursoEncontrado> lista = consultas.concursosPorCpf(argumentos.get(0));
        long tempo = Cronometro.milissegundos(inicio);
        lista.forEach(c -> saida.printf("%s | edital %s | código %d%n", c.orgao(), c.edital(), c.codigo()));
        saida.printf("-- %d concurso(s) em %d ms%n", lista.size(), tempo);
    }
}
