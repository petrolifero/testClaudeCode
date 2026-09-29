package br.org.leds.concursos.aplicacao;

import br.org.leds.concursos.dominio.CodigoConcurso;
import br.org.leds.concursos.dominio.Cpf;
import br.org.leds.concursos.dominio.RecursoNaoEncontradoException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BuscaServiceTest {

    private static final Cpf CPF = Cpf.of("438.150.926-98");
    private static final CodigoConcurso CODIGO = CodigoConcurso.of("61828450843");

    @Mock
    private BuscaRepository repository;

    @InjectMocks
    private BuscaService service;

    @Test
    void retornaConcursosDoCandidatoExistente() {
        List<ConcursoCompativel> esperado = List.of(new ConcursoCompativel("SEDU", "61828450843", "9/2016"));
        when(repository.existeCandidato(CPF)).thenReturn(true);
        when(repository.concursosCompativeis(CPF)).thenReturn(esperado);

        assertThat(service.concursosParaCandidato(CPF)).isEqualTo(esperado);
    }

    @Test
    void candidatoInexistenteGeraNaoEncontrado() {
        when(repository.existeCandidato(CPF)).thenReturn(false);

        assertThatThrownBy(() -> service.concursosParaCandidato(CPF))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessageContaining("438.150.926-98");
        verify(repository, never()).concursosCompativeis(any());
    }

    @Test
    void retornaCandidatosDoConcursoExistente() {
        List<CandidatoCompativel> esperado =
                List.of(new CandidatoCompativel("Ana", LocalDate.of(2004, 12, 18), "438.150.926-98"));
        when(repository.existeConcurso(CODIGO)).thenReturn(true);
        when(repository.candidatosCompativeis(CODIGO)).thenReturn(esperado);

        assertThat(service.candidatosParaConcurso(CODIGO)).isEqualTo(esperado);
    }

    @Test
    void concursoInexistenteGeraNaoEncontrado() {
        when(repository.existeConcurso(CODIGO)).thenReturn(false);

        assertThatThrownBy(() -> service.candidatosParaConcurso(CODIGO))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessageContaining("61828450843");
        verify(repository, never()).candidatosCompativeis(any());
    }
}
