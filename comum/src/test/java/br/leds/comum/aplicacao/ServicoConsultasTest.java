package br.leds.comum.aplicacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.leds.comum.dominio.CandidatoEncontrado;
import br.leds.comum.dominio.ConcursoEncontrado;
import br.leds.comum.erro.EntradaInvalida;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ServicoConsultasTest {

    /** Dublê que registra o que recebeu; prova que o serviço depende só da porta. */
    static class ConsultasFalsas implements ConsultasConcursos {
        final List<String> cpfs = new ArrayList<>();
        final List<Long> codigos = new ArrayList<>();

        @Override
        public List<ConcursoEncontrado> concursosPorCpf(String cpf) {
            cpfs.add(cpf);
            return List.of(new ConcursoEncontrado("PMES", "6/2022", 53691664314L));
        }

        @Override
        public List<CandidatoEncontrado> candidatosPorConcurso(long codigo) {
            codigos.add(codigo);
            return List.of(new CandidatoEncontrado("Ana", LocalDate.of(2004, 12, 18), "43815092698"));
        }
    }

    private final ConsultasFalsas falsas = new ConsultasFalsas();
    private final ServicoConsultas servico = new ServicoConsultas(falsas);

    @Test
    void normalizaCpfAntesDeConsultar() {
        assertEquals(1, servico.concursosPorCpf("438.150.926-98").size());
        assertEquals(List.of("43815092698"), falsas.cpfs);
    }

    @Test
    void cpfInvalidoNaoChegaAoBanco() {
        assertThrows(EntradaInvalida.class, () -> servico.concursosPorCpf("123"));
        assertEquals(List.of(), falsas.cpfs);
    }

    @Test
    void interpretaCodigoNumerico() {
        assertEquals(1, servico.candidatosPorConcurso(" 19750532858 ").size());
        assertEquals(List.of(19750532858L), falsas.codigos);
    }

    @Test
    void codigoNaoNumericoEhEntradaInvalida() {
        assertThrows(EntradaInvalida.class, () -> servico.candidatosPorConcurso("abc"));
        assertThrows(EntradaInvalida.class, () -> servico.candidatosPorConcurso(null));
        assertEquals(List.of(), falsas.codigos);
    }
}
