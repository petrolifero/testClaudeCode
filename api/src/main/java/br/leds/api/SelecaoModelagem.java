package br.leds.api;

import br.leds.comum.aplicacao.Modelagem;
import br.leds.comum.erro.ErroInfraestrutura;
import java.util.List;
import java.util.ServiceLoader;

/** Escolhe, entre as {@link Modelagem}s registradas no classpath, a que tem o nome configurado. */
final class SelecaoModelagem {

    private SelecaoModelagem() {
    }

    static Modelagem escolher(String nome) {
        List<Modelagem> disponiveis = ServiceLoader.load(Modelagem.class).stream().map(ServiceLoader.Provider::get).toList();
        return disponiveis.stream()
                .filter(m -> m.nome().equals(nome))
                .findFirst()
                .orElseThrow(() -> new ErroInfraestrutura("Modelagem '" + nome + "' não encontrada. Disponíveis: "
                        + disponiveis.stream().map(Modelagem::nome).toList()));
    }
}
