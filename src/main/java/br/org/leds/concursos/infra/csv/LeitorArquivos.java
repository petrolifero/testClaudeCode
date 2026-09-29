package br.org.leds.concursos.infra.csv;

import br.org.leds.concursos.dominio.Candidato;
import br.org.leds.concursos.dominio.CodigoConcurso;
import br.org.leds.concursos.dominio.Concurso;
import br.org.leds.concursos.dominio.Cpf;
import br.org.leds.concursos.dominio.DadoInvalidoException;
import br.org.leds.concursos.dominio.Profissoes;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.Reader;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Lê {@code candidatos.txt} e {@code concursos.txt} (CSV com cabeçalho, separado por vírgula,
 * listas entre aspas quando contêm vírgula) e converte para objetos de domínio.
 *
 * <p>A leitura é "tudo ou nada": qualquer linha inválida interrompe a importação com uma
 * mensagem indicando arquivo e linha, evitando carregar uma base parcialmente correta.</p>
 */
@Component
public class LeitorArquivos {

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/uuuu")
            .withResolverStyle(ResolverStyle.STRICT);
    private static final int COLUNAS = 4;

    private static final CSVFormat FORMATO = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setIgnoreEmptyLines(true)
            .setTrim(true)
            .get();

    public List<Candidato> lerCandidatos(Reader reader, String nomeArquivo) {
        List<Candidato> candidatos = ler(reader, nomeArquivo, this::paraCandidato);
        Set<Cpf> vistos = new HashSet<>();
        for (Candidato candidato : candidatos) {
            if (!vistos.add(candidato.cpf())) {
                throw new ArquivoInvalidoException(nomeArquivo, "CPF duplicado: " + candidato.cpf(), null);
            }
        }
        return candidatos;
    }

    public List<Concurso> lerConcursos(Reader reader, String nomeArquivo) {
        return ler(reader, nomeArquivo, this::paraConcurso);
    }

    private <T> List<T> ler(Reader reader, String nomeArquivo, Function<CSVRecord, T> conversor) {
        try (CSVParser parser = FORMATO.parse(reader)) {
            List<T> resultado = new ArrayList<>();
            for (CSVRecord registro : parser) {
                resultado.add(converter(registro, nomeArquivo, conversor));
            }
            return resultado;
        } catch (IOException | IllegalStateException | java.io.UncheckedIOException e) {
            throw new ArquivoInvalidoException(nomeArquivo, "falha ao ler o arquivo: " + e.getMessage(), e);
        }
    }

    private <T> T converter(CSVRecord registro, String nomeArquivo, Function<CSVRecord, T> conversor) {
        // getRecordNumber() começa em 1 após o cabeçalho; somamos 1 para refletir a linha física.
        long linha = registro.getRecordNumber() + 1;
        if (registro.size() != COLUNAS) {
            throw new ArquivoInvalidoException(nomeArquivo, linha,
                    "esperadas %d colunas, encontradas %d".formatted(COLUNAS, registro.size()), null);
        }
        try {
            return conversor.apply(registro);
        } catch (DadoInvalidoException | DateTimeParseException e) {
            throw new ArquivoInvalidoException(nomeArquivo, linha, e.getMessage(), e);
        }
    }

    private Candidato paraCandidato(CSVRecord r) {
        return new Candidato(
                r.get(0),
                LocalDate.parse(r.get(1), DATA),
                Cpf.of(r.get(2)),
                Profissoes.interpretar(r.get(3)));
    }

    private Concurso paraConcurso(CSVRecord r) {
        return new Concurso(
                r.get(0),
                r.get(1),
                CodigoConcurso.of(r.get(2)),
                Profissoes.interpretar(r.get(3)));
    }
}
