package br.org.leds.concursos.dominio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProfissoesTest {

    @Test
    void interpretaListaNormalizandoEspacosCaixaEDuplicatas() {
        assertThat(Profissoes.interpretar("[Carpinteiro,  assistente   administrativo , carpinteiro]"))
                .containsExactly("carpinteiro", "assistente administrativo");
    }

    @Test
    void interpretaListaComUmItem() {
        assertThat(Profissoes.interpretar("[professor de matemática]")).containsExactly("professor de matemática");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"carpinteiro", "[carpinteiro", "[]", "[ , ]"})
    void rejeitaListaInvalida(String texto) {
        assertThatThrownBy(() -> Profissoes.interpretar(texto)).isInstanceOf(DadoInvalidoException.class);
    }
}
