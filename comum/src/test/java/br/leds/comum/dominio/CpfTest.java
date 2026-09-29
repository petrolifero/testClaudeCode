package br.leds.comum.dominio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.leds.comum.erro.EntradaInvalida;
import org.junit.jupiter.api.Test;

class CpfTest {

    @Test
    void normalizaEFormata() {
        assertEquals("43815092698", Cpf.normalizar("438.150.926-98"));
        assertEquals("438.150.926-98", Cpf.formatar("43815092698"));
    }

    @Test
    void rejeitaTamanhoErrado() {
        assertThrows(EntradaInvalida.class, () -> Cpf.normalizar("123"));
        assertThrows(EntradaInvalida.class, () -> Cpf.normalizar(null));
    }
}
