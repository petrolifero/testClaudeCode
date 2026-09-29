package br.leds.comum.csv;

import br.leds.comum.dominio.Candidato;
import br.leds.comum.dominio.Concurso;
import br.leds.comum.dominio.Cpf;
import br.leds.comum.erro.EntradaInvalida;
import br.leds.comum.erro.ErroAplicacao;


import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * Lê os dois CSVs do desafio. Formato: primeira linha é cabeçalho; listas vêm entre colchetes e, quando
 * têm mais de um item, o campo inteiro vem entre aspas (por causa das vírgulas internas).
 */
public final class CsvLeitor {

    private static final DateTimeFormatter DATA =
            DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);

    private CsvLeitor() {
    }

    public static List<Candidato> lerCandidatos(Path arquivo) {
        List<Candidato> resultado = new ArrayList<>();
        lerLinhas(arquivo, 4, (numero, campos) -> {
            try {
                resultado.add(new Candidato(
                        campos.get(0).trim(),
                        LocalDate.parse(campos.get(1).trim(), DATA),
                        Cpf.normalizar(campos.get(2)),
                        lista(campos.get(3))));
            } catch (DateTimeParseException e) {
                throw new EntradaInvalida("Data inválida '" + campos.get(1) + "'", e);
            }
        });
        return resultado;
    }

    public static List<Concurso> lerConcursos(Path arquivo) {
        List<Concurso> resultado = new ArrayList<>();
        lerLinhas(arquivo, 4, (numero, campos) -> {
            try {
                resultado.add(new Concurso(
                        campos.get(0).trim(),
                        campos.get(1).trim(),
                        Long.parseLong(campos.get(2).trim()),
                        lista(campos.get(3))));
            } catch (NumberFormatException e) {
                throw new EntradaInvalida("Código de concurso inválido '" + campos.get(2) + "'", e);
            }
        });
        return resultado;
    }

    /** "[a, b ,c]" vira [a, b, c] (minúsculas, sem vazios e sem repetições, mantendo a ordem). */
    static List<String> lista(String campo) {
        String texto = campo.trim();
        if (!texto.startsWith("[") || !texto.endsWith("]")) {
            throw new EntradaInvalida("Lista deve estar entre colchetes: '" + campo + "'");
        }
        LinkedHashSet<String> itens = new LinkedHashSet<>();
        for (String item : texto.substring(1, texto.length() - 1).split(",")) {
            String limpo = item.trim().toLowerCase(Locale.ROOT);
            if (!limpo.isEmpty()) {
                itens.add(limpo);
            }
        }
        return List.copyOf(itens);
    }

    /** Divide uma linha CSV respeitando aspas (com "" como aspas escapadas). */
    static List<String> dividir(String linha) {
        List<String> campos = new ArrayList<>();
        StringBuilder atual = new StringBuilder();
        boolean entreAspas = false;
        for (int i = 0; i < linha.length(); i++) {
            char c = linha.charAt(i);
            if (c == '"') {
                if (entreAspas && i + 1 < linha.length() && linha.charAt(i + 1) == '"') {
                    atual.append('"');
                    i++;
                } else {
                    entreAspas = !entreAspas;
                }
            } else if (c == ',' && !entreAspas) {
                campos.add(atual.toString());
                atual.setLength(0);
            } else {
                atual.append(c);
            }
        }
        campos.add(atual.toString());
        return campos;
    }

    private interface ProcessadorLinha {
        void processar(int numero, List<String> campos);
    }

    private static void lerLinhas(Path arquivo, int colunas, ProcessadorLinha processador) {
        try (BufferedReader leitor = Files.newBufferedReader(arquivo, StandardCharsets.UTF_8)) {
            String linha = leitor.readLine(); // cabeçalho
            if (linha == null) {
                throw new EntradaInvalida("Arquivo vazio: " + arquivo);
            }
            int numero = 1;
            while ((linha = leitor.readLine()) != null) {
                numero++;
                if (linha.isBlank()) {
                    continue;
                }
                List<String> campos = dividir(linha);
                if (campos.size() != colunas) {
                    throw new EntradaInvalida(arquivo + ":" + numero + ": esperado " + colunas
                            + " colunas, encontrado " + campos.size());
                }
                try {
                    processador.processar(numero, campos);
                } catch (EntradaInvalida e) {
                    throw new EntradaInvalida(arquivo + ":" + numero + ": " + e.getMessage(), e);
                }
            }
        } catch (NoSuchFileException e) {
            throw new EntradaInvalida("Arquivo não encontrado: " + arquivo, e);
        } catch (IOException e) {
            throw new ErroAplicacao("Falha ao ler " + arquivo + ": " + e.getMessage(), e);
        }
    }
}
