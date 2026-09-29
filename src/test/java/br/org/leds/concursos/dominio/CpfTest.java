package br.org.leds.concursos.dominio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CpfTest {

    @ParameterizedTest
    @ValueSource(strings = {"438.150.926-98", "43815092698", " 438.150.926-98 "})
    void aceitaCpfComOuSemPontuacao(String texto) {
        Cpf cpf = Cpf.of(texto);

        assertThat(cpf.digitos()).isEqualTo("43815092698");
        assertThat(cpf.formatado()).isEqualTo("438.150.926-98");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"438.150.926", "abc.def.ghi-jk", "438.150.926-988", "438/150/926-98"})
    void rejeitaFormatoInvalido(String texto) {
        assertThatThrownBy(() -> Cpf.of(texto))
                .isInstanceOf(DadoInvalidoException.class)
                .hasMessageContaining("formato inválido");
    }

    @ParameterizedTest
    @ValueSource(strings = {"438.150.926-99", "182.845.084-34", "111.111.111-11", "000.000.000-00"})
    void rejeitaDigitosVerificadoresInvalidos(String texto) {
        assertThatThrownBy(() -> Cpf.of(texto))
                .isInstanceOf(DadoInvalidoException.class)
                .hasMessageContaining("dígitos verificadores");
    }

    @Test
    void cpfsIguaisSaoIguais() {
        assertThat(Cpf.of("438.150.926-98")).isEqualTo(Cpf.of("43815092698"));
    }
}
