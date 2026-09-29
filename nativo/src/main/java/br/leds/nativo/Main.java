package br.leds.nativo;

import br.leds.comum.Banco;
import br.leds.comum.Cli;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/** Ponto de entrada da solução nativa (banco padrão: "nativo"). */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        // UTF-8 explícito: acentos saem certos independentemente do locale do terminal
        PrintStream saida = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        PrintStream erro = new PrintStream(System.err, true, StandardCharsets.UTF_8);
        int codigo = Cli.executar(args, new RepositorioNativo(new Banco("nativo")), saida, erro);
        System.exit(codigo);
    }
}
