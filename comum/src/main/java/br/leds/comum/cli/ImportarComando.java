package br.leds.comum.cli;

import br.leds.comum.aplicacao.ServicoImportacao;
import br.leds.comum.csv.CsvLeitor;
import br.leds.comum.dominio.Candidato;
import br.leds.comum.dominio.Concurso;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;

final class ImportarComando implements Comando {

    private final ServicoImportacao importacao;

    ImportarComando(ServicoImportacao importacao) {
        this.importacao = importacao;
    }

    @Override
    public String nome() {
        return "importar";
    }

    @Override
    public String uso() {
        return "importar <candidatos.csv> <concursos.csv>";
    }

    @Override
    public String descricao() {
        return "cria o esquema e substitui os dados";
    }

    @Override
    public int quantidadeArgumentos() {
        return 2;
    }

    @Override
    public void executar(List<String> argumentos, PrintStream saida) {
        List<Candidato> candidatos = CsvLeitor.lerCandidatos(Path.of(argumentos.get(0)));
        List<Concurso> concursos = CsvLeitor.lerConcursos(Path.of(argumentos.get(1)));
        long inicio = System.nanoTime();
        importacao.importar(candidatos, concursos);
        saida.printf("Importados %d candidatos e %d concursos em %d ms%n",
                candidatos.size(), concursos.size(), Cronometro.milissegundos(inicio));
    }
}
