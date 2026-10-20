package com.gupyclone.triagem;

import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Os dois insumos da analise que as portas dos modulos donos nao carregam: o
 * texto do curriculo e os criterios de aceite da vaga.
 *
 * <p>Nenhum dos dois cabe numa porta de travessia. {@code DadosDoCurriculo}
 * deixa o texto de fora porque e uma das duas colunas grandes da tabela, e
 * {@code DadosDaVaga} deixa os criterios de fora porque sao parte interna do
 * agregado Vaga. Os dois existem para uma coisa so — decidir uma candidatura —
 * e e a triagem que decide, entao e aqui que eles sao lidos, por SQL, como o
 * {@link AnalisadorFullTextSearch} ja lia o {@code texto_busca} do curriculo.
 *
 * <p>Por SQL, e nao pelos repositorios de vagas e de curriculos: repositorio de
 * outro modulo nao entra neste, e a leitura toca so as colunas que a decisao
 * usa.
 */
@Component
public class InsumosDaAnalise {

    private static final String SQL_TEXTO = """
            select texto
              from curriculo
             where id = ?
            """;

    /** Ordem por id: e a ordem em que o RH descreveu os criterios na vaga. */
    private static final String SQL_CRITERIOS = """
            select id, descricao, expressao, peso, obrigatorio
              from criterio_aceite
             where vaga_id = ?
             order by id
            """;

    private final JdbcClient jdbc;

    public InsumosDaAnalise(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * @return o texto extraido do curriculo, vindo da camada de texto ou do OCR
     * @throws IllegalStateException se o curriculo nao estiver mais no banco
     */
    public String textoDoCurriculo(Long curriculoId) {
        return jdbc.sql(SQL_TEXTO).param(curriculoId).query(String.class).optional()
                .orElseThrow(() -> new IllegalStateException(
                        "curriculo " + curriculoId + " nao esta mais no banco; analise abortada"));
    }

    /** @return vazia para vaga sem criterio nenhum, que a fatia de vagas permite */
    public List<ContextoDaAnalise.Criterio> criteriosDaVaga(Long vagaId) {
        return jdbc.sql(SQL_CRITERIOS).param(vagaId)
                .query((registro, numero) -> new ContextoDaAnalise.Criterio(
                        registro.getLong("id"),
                        registro.getString("descricao"),
                        registro.getString("expressao"),
                        registro.getInt("peso"),
                        registro.getBoolean("obrigatorio")))
                .list();
    }
}
