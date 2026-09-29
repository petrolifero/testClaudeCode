package br.leds.comum.aplicacao;

import br.leds.comum.dominio.Candidato;
import br.leds.comum.dominio.Concurso;
import java.util.List;

/** Porta de <b>escrita</b>: preparação do esquema e carga em massa. Separada de {@link ConsultasConcursos}. */
public interface CargaDados {

    /** Cria tabelas e índices caso ainda não existam. */
    void criarEsquema();

    /** Apaga os dados atuais e grava os informados, tudo em uma única transação. */
    void substituirDados(List<Candidato> candidatos, List<Concurso> concursos);
}
