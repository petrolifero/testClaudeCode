package br.leds.comum.aplicacao;

import br.leds.comum.dominio.Candidato;
import br.leds.comum.dominio.Concurso;
import java.util.List;
import java.util.Objects;

/** Caso de uso de carga: garante o esquema e substitui os dados. Depende só da porta {@link CargaDados}. */
public class ServicoImportacao {

    private final CargaDados carga;

    public ServicoImportacao(CargaDados carga) {
        this.carga = Objects.requireNonNull(carga);
    }

    public void importar(List<Candidato> candidatos, List<Concurso> concursos) {
        carga.criarEsquema();
        carga.substituirDados(candidatos, concursos);
    }
}
