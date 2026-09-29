package br.leds.comum.csv;

import br.leds.comum.dominio.Cpf;
import br.leds.comum.erro.ErroAplicacao;


import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Gera CSVs sintéticos no mesmo formato do desafio, para comparar as duas soluções com volume. */
public final class Gerador {

    private static final String[] PROFISSOES = {
        "engenheiro civil", "encanador", "arquiteto", "pintor", "analista de sistemas", "carpinteiro",
        "marceneiro", "eletricista", "pedreiro", "professor de matemática", "professor de português",
        "médico", "enfermeiro", "contador", "advogado", "motorista", "vigilante", "auxiliar administrativo",
        "técnico de informática", "bibliotecário", "psicólogo", "assistente social", "nutricionista",
        "agrônomo", "veterinário", "geólogo", "topógrafo", "químico", "biólogo", "jornalista"
    };
    private static final String[] ORGAOS = {
        "PMES", "SECONT", "SEDURB", "SEAG", "DER", "SEFAZ", "SESA", "SEDU", "IDAF", "DETRAN", "PCES", "CBMES"
    };
    private static final String[] NOMES = {
        "Ana", "Antônio", "Emilly", "Evelyn", "Sabrina", "Enzo", "Melissa", "João", "Maria", "Pedro", "Lucas",
        "Beatriz", "Gabriel", "Laura", "Rafael", "Camila"
    };
    private static final String[] SOBRENOMES = {
        "Araújo", "Carvalho", "Almeida", "Moraes", "Moreira", "Ramos", "Farias", "Azevedo", "Silva", "Souza",
        "Oliveira", "Santos", "Lima", "Costa", "Pereira", "Rodrigues"
    };
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/uuuu");

    private Gerador() {
    }

    public static void gerar(int candidatos, int concursos, Path pasta) {
        Random azar = new Random(42); // semente fixa: mesma saída sempre
        try {
            Files.createDirectories(pasta);
            try (BufferedWriter saida = Files.newBufferedWriter(pasta.resolve("candidatos.csv"), StandardCharsets.UTF_8)) {
                saida.write("Nome,Data de Nascimento,CPF,Profissões\n");
                Set<Long> usados = new HashSet<>();
                for (int i = 0; i < candidatos; i++) {
                    long numero;
                    do {
                        numero = (long) (azar.nextDouble() * 99_999_999_999L);
                    } while (!usados.add(numero));
                    String cpf = Cpf.formatar(String.format("%011d", numero));
                    LocalDate nascimento = LocalDate.of(1950 + azar.nextInt(56), 1 + azar.nextInt(12), 1 + azar.nextInt(28));
                    String nome = NOMES[azar.nextInt(NOMES.length)] + " " + SOBRENOMES[azar.nextInt(SOBRENOMES.length)];
                    saida.write(nome + "," + DATA.format(nascimento) + "," + cpf + "," + campoLista(sorteio(azar, 1, 4), ", ") + "\n");
                }
            }
            try (BufferedWriter saida = Files.newBufferedWriter(pasta.resolve("concursos.csv"), StandardCharsets.UTF_8)) {
                saida.write("Órgão,Edital,Código do Concurso,Lista de vagas\n");
                for (int i = 0; i < concursos; i++) {
                    String edital = (1 + azar.nextInt(20)) + "/" + (2015 + azar.nextInt(10));
                    long codigo = 10_000_000_000L + (long) (azar.nextDouble() * 89_999_999_999L);
                    saida.write(ORGAOS[azar.nextInt(ORGAOS.length)] + "," + edital + "," + codigo + ","
                            + campoLista(sorteio(azar, 1, 5), ",") + "\n");
                }
            }
        } catch (IOException e) {
            throw new ErroAplicacao("Falha ao gerar arquivos em " + pasta + ": " + e.getMessage(), e);
        }
    }

    private static List<String> sorteio(Random azar, int minimo, int maximo) {
        int quantidade = minimo + azar.nextInt(maximo - minimo + 1);
        List<String> itens = new ArrayList<>();
        while (itens.size() < quantidade) {
            String profissao = PROFISSOES[azar.nextInt(PROFISSOES.length)];
            if (!itens.contains(profissao)) {
                itens.add(profissao);
            }
        }
        return itens;
    }

    private static String campoLista(List<String> itens, String separador) {
        String lista = "[" + String.join(separador, itens) + "]";
        return itens.size() > 1 ? "\"" + lista + "\"" : lista;
    }
}
