package br.leds.comum.aplicacao;

import br.leds.comum.dominio.CandidatoEncontrado;
import br.leds.comum.dominio.ConcursoEncontrado;
import br.leds.comum.dominio.Cpf;
import br.leds.comum.erro.EntradaInvalida;
import java.util.List;
import java.util.Objects;

/**
 * Casos de uso de consulta. É o <b>único</b> lugar que valida/normaliza a entrada textual, então CLI e REST
 * herdam as mesmas regras e mensagens. Depende só da porta {@link ConsultasConcursos} (Dependency Inversion).
 */
public class ServicoConsultas {

    private final ConsultasConcursos consultas;

    public ServicoConsultas(ConsultasConcursos consultas) {
        this.consultas = Objects.requireNonNull(consultas);
    }

    /** @param cpf com ou sem pontuação */
    public List<ConcursoEncontrado> concursosPorCpf(String cpf) {
        return consultas.concursosPorCpf(Cpf.normalizar(cpf));
    }

    /** @param codigo código do concurso, somente dígitos */
    public List<CandidatoEncontrado> candidatosPorConcurso(String codigo) {
        return consultas.candidatosPorConcurso(interpretarCodigo(codigo));
    }

    private static long interpretarCodigo(String codigo) {
        try {
            return Long.parseLong(codigo == null ? "" : codigo.trim());
        } catch (NumberFormatException e) {
            throw new EntradaInvalida("Código de concurso deve ser numérico: '" + codigo + "'", e);
        }
    }
}
