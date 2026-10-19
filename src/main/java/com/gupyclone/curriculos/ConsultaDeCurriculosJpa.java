package com.gupyclone.curriculos;

import java.util.Optional;
import org.springframework.stereotype.Component;

/** Porta de curriculos atendida por projecao, sem carregar a entidade. */
// Classe propria em vez de o CurriculoRepository implementar a porta: aqui a
// projecao tambem evita arrastar o PDF inteiro para a memoria, e o repositorio
// continua detalhe interno de curriculos.
@Component
public class ConsultaDeCurriculosJpa implements ConsultaDeCurriculos {

    private final CurriculoRepository curriculos;

    public ConsultaDeCurriculosJpa(CurriculoRepository curriculos) {
        this.curriculos = curriculos;
    }

    @Override
    public Optional<DadosDoCurriculo> porId(Long id) {
        return curriculos.dadosPorId(id);
    }
}
