package br.org.leds.concursos.dominio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CodigoConcursoTest {

    @Test
    void aceitaOnzeDigitos() {
        assertThat(CodigoConcurso.of(" 61828450843 ").valor()).isEqualTo("61828450843");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"6182845084", "618284508431", "6182845084a", "618.284.508-43"})
    void rejeitaCodigoInvalido(String valor) {
        assertThatThrownBy(() -> CodigoConcurso.of(valor)).isInstanceOf(DadoInvalidoException.class);
    }
}
