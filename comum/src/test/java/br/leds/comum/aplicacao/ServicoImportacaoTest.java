package br.leds.comum.aplicacao;

import static org.junit.jupiter.api.Assertions.assertEquals;

import br.leds.comum.dominio.Candidato;
import br.leds.comum.dominio.Concurso;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ServicoImportacaoTest {

    @Test
    void criaEsquemaAntesDeSubstituirOsDados() {
        List<String> chamadas = new ArrayList<>();
        CargaDados carga = new CargaDados() {
            @Override
            public void criarEsquema() {
                chamadas.add("esquema");
            }

            @Override
            public void substituirDados(List<Candidato> candidatos, List<Concurso> concursos) {
                chamadas.add("dados");
            }
        };
        new ServicoImportacao(carga).importar(List.of(), List.of());
        assertEquals(List.of("esquema", "dados"), chamadas);
    }
}
