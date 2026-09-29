package br.leds.comum;

import java.io.PrintStream;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Interface de linha de comando compartilhada pelas duas soluções. */
public final class Cli {

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/uuuu");

    private static final String AJUDA = """
            Uso: <comando> [argumentos]

              importar <candidatos.csv> <concursos.csv>   cria o esquema e substitui os dados
              concursos-por-cpf <cpf>                     concursos compatíveis com o candidato
              candidatos-por-concurso <codigo>            candidatos compatíveis com o concurso
              gerar <candidatos> <concursos> <pasta>      gera CSVs sintéticos (não usa banco)

            Conexão (variáveis de ambiente): DB_URL, DB_USER, DB_PASSWORD
            """;

    private Cli() {
    }

    /**
     * @return código de saída do processo (0 = sucesso, 1 = erro de uso/dados/banco)
     */
    public static int executar(String[] args, RepositorioConcursos repositorio, PrintStream saida, PrintStream erro) {
        try {
            if (args.length == 0) {
                erro.print(AJUDA);
                return 1;
            }
            switch (args[0]) {
                case "importar" -> importar(exigir(args, 3), repositorio, saida);
                case "concursos-por-cpf" -> concursosPorCpf(exigir(args, 2), repositorio, saida);
                case "candidatos-por-concurso" -> candidatosPorConcurso(exigir(args, 2), repositorio, saida);
                case "gerar" -> gerar(exigir(args, 4), saida);
                default -> throw new ErroAplicacao("Comando desconhecido: " + args[0] + "\n" + AJUDA);
            }
            return 0;
        } catch (ErroAplicacao e) {
            erro.println("Erro: " + e.getMessage());
            return 1;
        }
    }

    private static String[] exigir(String[] args, int quantidade) {
        if (args.length != quantidade) {
            throw new ErroAplicacao("Número de argumentos inválido para '" + args[0] + "'.\n" + AJUDA);
        }
        return args;
    }

    private static void gerar(String[] args, PrintStream saida) {
        try {
            Gerador.gerar(Integer.parseInt(args[1]), Integer.parseInt(args[2]), Path.of(args[3]));
        } catch (NumberFormatException e) {
            throw new ErroAplicacao("Quantidades devem ser números inteiros.", e);
        }
        saida.println("Arquivos gerados em " + args[3]);
    }

    private static void importar(String[] args, RepositorioConcursos repositorio, PrintStream saida) {
        List<Candidato> candidatos = CsvLeitor.lerCandidatos(Path.of(args[1]));
        List<Concurso> concursos = CsvLeitor.lerConcursos(Path.of(args[2]));
        long inicio = System.nanoTime();
        repositorio.criarEsquema();
        repositorio.substituirDados(candidatos, concursos);
        saida.printf("Importados %d candidatos e %d concursos em %d ms%n",
                candidatos.size(), concursos.size(), milissegundos(inicio));
    }

    private static void concursosPorCpf(String[] args, RepositorioConcursos repositorio, PrintStream saida) {
        String cpf = Cpf.normalizar(args[1]);
        long inicio = System.nanoTime();
        List<ConcursoEncontrado> lista = repositorio.concursosPorCpf(cpf);
        long tempo = milissegundos(inicio);
        lista.forEach(c -> saida.printf("%s | edital %s | código %d%n", c.orgao(), c.edital(), c.codigo()));
        saida.printf("-- %d concurso(s) em %d ms%n", lista.size(), tempo);
    }

    private static void candidatosPorConcurso(String[] args, RepositorioConcursos repositorio, PrintStream saida) {
        long codigo;
        try {
            codigo = Long.parseLong(args[1].trim());
        } catch (NumberFormatException e) {
            throw new ErroAplicacao("Código de concurso deve ser numérico: '" + args[1] + "'", e);
        }
        long inicio = System.nanoTime();
        List<CandidatoEncontrado> lista = repositorio.candidatosPorConcurso(codigo);
        long tempo = milissegundos(inicio);
        lista.forEach(c -> saida.printf("%s | %s | %s%n", c.nome(), DATA.format(c.dataNascimento()), Cpf.formatar(c.cpf())));
        saida.printf("-- %d candidato(s) em %d ms%n", lista.size(), tempo);
    }

    private static long milissegundos(long inicioNanos) {
        return (System.nanoTime() - inicioNanos) / 1_000_000;
    }
}
