package br.leds.comum.cli;

import br.leds.comum.aplicacao.ServicoConsultas;
import br.leds.comum.aplicacao.ServicoImportacao;
import br.leds.comum.erro.EntradaInvalida;
import br.leds.comum.erro.ErroAplicacao;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Despacha o primeiro argumento para o {@link Comando} correspondente e traduz erros em código de saída. */
public final class Cli {

    private final Map<String, Comando> comandos = new LinkedHashMap<>();

    public Cli(List<Comando> comandos) {
        comandos.forEach(c -> this.comandos.put(c.nome(), c));
    }

    /** CLI com os quatro comandos do projeto. */
    public static Cli padrao(ServicoConsultas consultas, ServicoImportacao importacao) {
        return new Cli(List.of(
                new ImportarComando(importacao),
                new ConcursosPorCpfComando(consultas),
                new CandidatosPorConcursoComando(consultas),
                new GerarComando()));
    }

    /** @return código de saída do processo (0 = sucesso, 1 = erro de uso/dados/banco) */
    public int executar(String[] args, PrintStream saida, PrintStream erro) {
        try {
            if (args.length == 0) {
                erro.print(ajuda());
                return 1;
            }
            Comando comando = comandos.get(args[0]);
            if (comando == null) {
                throw new EntradaInvalida("Comando desconhecido: " + args[0] + "\n" + ajuda());
            }
            List<String> argumentos = Arrays.asList(args).subList(1, args.length);
            if (argumentos.size() != comando.quantidadeArgumentos()) {
                throw new EntradaInvalida("Número de argumentos inválido.\nUso: " + comando.uso());
            }
            comando.executar(argumentos, saida);
            return 0;
        } catch (ErroAplicacao e) {
            erro.println("Erro: " + e.getMessage());
            return 1;
        }
    }

    String ajuda() {
        StringBuilder texto = new StringBuilder("Uso: <comando> [argumentos]\n\n");
        comandos.values().forEach(c -> texto.append(String.format("  %-48s %s%n", c.uso(), c.descricao())));
        texto.append("\nConexão (variáveis de ambiente): DB_URL, DB_USER, DB_PASSWORD\n");
        return texto.toString();
    }
}
