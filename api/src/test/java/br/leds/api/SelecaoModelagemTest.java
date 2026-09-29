package br.leds.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.leds.comum.erro.ErroInfraestrutura;
import org.junit.jupiter.api.Test;

class SelecaoModelagemTest {

    @Test
    void descobreAsDuasModelagensPeloServiceLoader() {
        assertEquals("academico", SelecaoModelagem.escolher("academico").nome());
        assertEquals("nativo", SelecaoModelagem.escolher("nativo").nome());
    }

    @Test
    void nomeDesconhecidoListaAsDisponiveis() {
        ErroInfraestrutura e = assertThrows(ErroInfraestrutura.class, () -> SelecaoModelagem.escolher("xpto"));
        assertTrue(e.getMessage().contains("academico") && e.getMessage().contains("nativo"), e.getMessage());
    }
}
