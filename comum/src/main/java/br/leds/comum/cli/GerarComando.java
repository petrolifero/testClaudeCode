package br.leds.comum.cli;

import br.leds.comum.csv.Gerador;
import br.leds.comum.erro.EntradaInvalida;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;

final class GerarComando implements Comando {

    @Override
    public String nome() {
        return "gerar";
    }

    @Override
    public String uso() {
        return "gerar <candidatos> <concursos> <pasta>";
    }

    @Override
    public String descricao() {
        return "gera CSVs sintéticos (não usa banco)";
    }

    @Override
    public int quantidadeArgumentos() {
        return 3;
    }

    @Override
    public void executar(List<String> argumentos, PrintStream saida) {
        try {
            Gerador.gerar(Integer.parseInt(argumentos.get(0)), Integer.parseInt(argumentos.get(1)),
                    Path.of(argumentos.get(2)));
        } catch (NumberFormatException e) {
            throw new EntradaInvalida("Quantidades devem ser números inteiros.", e);
        }
        saida.println("Arquivos gerados em " + argumentos.get(2));
    }
}
