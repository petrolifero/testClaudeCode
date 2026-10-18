package com.gupyclone.vagas;

import com.gupyclone.comum.BuscaTextual;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Recusa, na publicacao, expressao de criterio que nenhum curriculo poderia
 * atender.
 *
 * <p>{@code websearch_to_tsquery} nunca estoura com texto de gente, o que e
 * bom — mas tem um efeito colateral ruim: expressao formada so por palavra
 * vazia ou operador ({@code "-"}, {@code "ou"}, {@code "com"}, {@code "de"})
 * reduz a uma tsquery VAZIA, que nao casa com nada. O criterio entao ficava
 * impossivel de atender em silencio: se fosse obrigatorio, reprovava todo
 * candidato com motivo apontando um criterio que ninguem jamais cumpriria, e
 * nada no sistema avisava.
 *
 * <p>Validar na publicacao e o unico momento em que o RH pode corrigir.
 */
@Component
public class ValidadorDeExpressaoDeBusca {

    private final JdbcClient jdbc;

    public ValidadorDeExpressaoDeBusca(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * @throws IllegalArgumentException se a expressao nao gera consulta alguma
     */
    public void exigirUtil(String descricao, String expressao) {
        if (!geraConsulta(expressao)) {
            throw new IllegalArgumentException("a expressão de busca do critério \"" + descricao
                    + "\" não procura nada: \"" + expressao
                    + "\" é formada apenas por palavras muito comuns ou operadores."
                    + " Use termos que devam aparecer no currículo, por exemplo \"java or jvm\".");
        }
    }

    public boolean geraConsulta(String expressao) {
        if (expressao == null || expressao.isBlank()) {
            return false;
        }
        Boolean util = jdbc.sql("select websearch_to_tsquery('" + BuscaTextual.CONFIGURACAO
                        + "', :expressao) <> ''::tsquery")
                .param("expressao", expressao)
                .query(Boolean.class)
                .single();
        return Boolean.TRUE.equals(util);
    }
}
