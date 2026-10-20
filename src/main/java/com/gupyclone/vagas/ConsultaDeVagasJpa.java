package com.gupyclone.vagas;

import java.util.Optional;
import org.springframework.stereotype.Component;

/** Porta de vagas atendida por projecao, sem carregar a entidade. */
// Classe propria em vez de o VagaRepository implementar a porta: assim o
// repositorio continua detalhe interno de vagas e quem le do lado de fora nao
// recebe as escritas do JpaRepository de brinde.
@Component
public class ConsultaDeVagasJpa implements ConsultaDeVagas {

    private final VagaRepository vagas;

    public ConsultaDeVagasJpa(VagaRepository vagas) {
        this.vagas = vagas;
    }

    @Override
    public Optional<DadosDaVaga> porId(Long id) {
        return vagas.dadosPorId(id);
    }
}
