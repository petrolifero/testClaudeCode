package br.leds.comum.aplicacao;

import javax.sql.DataSource;

/**
 * Uma estratégia de armazenamento no PostgreSQL (ex.: acadêmica, nativa). Cada módulo fornece a sua e a registra em
 * {@code META-INF/services}; quem monta a aplicação (CLI, API) escolhe uma sem conhecer as classes concretas
 * (Open/Closed: nova modelagem = novo módulo, sem editar os consumidores).
 */
public interface Modelagem {

    /** Identificador estável, usado na configuração e como nome padrão do banco (ex.: "academico"). */
    String nome();

    ConsultasConcursos consultas(DataSource dataSource);

    CargaDados carga(DataSource dataSource);
}
