package br.leds.comum.cli;

import java.io.PrintStream;
import java.util.List;

/** Um subcomando da CLI. Novos comandos são novas classes registradas em {@link Cli}, sem alterar as existentes. */
public interface Comando {

    String nome();

    /** Ex.: {@code importar <candidatos.csv> <concursos.csv>}. */
    String uso();

    String descricao();

    /** Quantidade de argumentos que vêm depois do nome do comando. */
    int quantidadeArgumentos();

    void executar(List<String> argumentos, PrintStream saida);
}
