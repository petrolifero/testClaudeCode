package br.org.leds.concursos.dominio;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Utilitário para interpretar e normalizar listas de profissões/vagas no formato
 * {@code [carpinteiro, marceneiro]} usado nos arquivos de entrada.
 */
public final class Profissoes {

    private static final Locale PT_BR = Locale.of("pt", "BR");

    private Profissoes() {
    }

    /**
     * Converte o texto {@code [a, b, c]} num conjunto ordenado, sem duplicatas, de nomes normalizados.
     *
     * @throws DadoInvalidoException se o texto não estiver entre colchetes ou a lista estiver vazia
     */
    public static Set<String> interpretar(String lista) {
        if (lista == null) {
            throw new DadoInvalidoException("Lista de profissões ausente");
        }
        String texto = lista.strip();
        if (!texto.startsWith("[") || !texto.endsWith("]")) {
            throw new DadoInvalidoException("Lista de profissões deve estar entre colchetes: " + lista);
        }
        Set<String> nomes = Arrays.stream(texto.substring(1, texto.length() - 1).split(","))
                .map(Profissoes::normalizar)
                .filter(nome -> !nome.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (nomes.isEmpty()) {
            throw new DadoInvalidoException("Lista de profissões vazia: " + lista);
        }
        return nomes;
    }

    /** Remove espaços extras e padroniza para minúsculas, de modo que "Marceneiro " == "marceneiro". */
    public static String normalizar(String nome) {
        return nome.strip().replaceAll("\\s+", " ").toLowerCase(PT_BR);
    }
}
