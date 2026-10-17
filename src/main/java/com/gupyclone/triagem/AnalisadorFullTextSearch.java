package com.gupyclone.triagem;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Analise por busca textual no proprio PostgreSQL.
 *
 * <p>Quem decide se um criterio foi atendido e o banco, com a configuracao
 * {@code portugues_sem_acento} criada pela migracao V1 e com a coluna gerada
 * {@code curriculo.texto_busca}. Isso da de graca tres coisas que uma
 * comparacao de strings em Java nao daria: insensibilidade a acento e a caixa,
 * radicalizacao ("graduação" casa com "graduacao" e com "pos-graduacao") e um
 * indice GIN para quando a tabela crescer.
 *
 * <p>{@code websearch_to_tsquery}, e nao {@code to_tsquery}: a expressao do
 * criterio e texto livre digitado pelo RH ({@code "java or jvm"},
 * {@code "-php"}, frase entre aspas) e essa funcao nunca estoura erro de
 * sintaxe com entrada de gente.
 */
@Component
public class AnalisadorFullTextSearch implements AnalisadorDeCandidatura {

    /** Valor de {@code gupy.triagem.estrategia} que escolhe este analisador. */
    public static final String NOME = "full-text-search";

    private static final int PONTUACAO_MAXIMA = 100;

    /**
     * Uma consulta so, com os criterios vindos como linhas de um {@code values},
     * em vez de uma consulta por criterio: o custo de ir ao banco domina o de
     * casar o tsvector, e uma vaga com dez criterios nao deve custar dez idas.
     *
     * <p>O {@code values} e montado com o numero de criterios, mas todo valor
     * continua sendo parametro ligado — nada de expressao do RH entra no texto
     * do SQL. A coluna {@code atende} vem por criterio (em vez de so os ids que
     * casaram) para que um curriculo que desapareceu apareca como ausencia de
     * linhas, e nao como reprovacao silenciosa.
     */
    private static final String SQL_ATENDIMENTO = """
            select c.id, cv.texto_busca @@ websearch_to_tsquery('portugues_sem_acento', c.expressao) as atende
              from curriculo cv
             cross join (values %s) as c (id, expressao)
             where cv.id = :curriculo
            """;

    private final JdbcClient jdbc;

    public AnalisadorFullTextSearch(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String nome() {
        return NOME;
    }

    @Override
    public ResultadoDaAnalise analisar(ContextoDaAnalise contexto) {
        Set<Long> atendidos = idsAtendidos(contexto);

        List<String> descricoesAtendidas = new ArrayList<>();
        List<String> descricoesNaoAtendidas = new ArrayList<>();
        List<String> rotulosNaoAtendidos = new ArrayList<>();
        int pesoAtendido = 0;
        boolean faltaObrigatorio = false;
        for (ContextoDaAnalise.Criterio criterio : contexto.criterios()) {
            if (atendidos.contains(criterio.id())) {
                pesoAtendido += criterio.peso();
                descricoesAtendidas.add(criterio.descricao());
            } else {
                descricoesNaoAtendidas.add(criterio.descricao());
                rotulosNaoAtendidos.add(criterio.obrigatorio()
                        ? criterio.descricao() + " (obrigatório)"
                        : criterio.descricao());
                faltaObrigatorio = faltaObrigatorio || criterio.obrigatorio();
            }
        }

        int pontuacao = pontuacao(pesoAtendido, contexto.pesoTotal());
        String motivo = motivo(descricoesAtendidas, rotulosNaoAtendidos, pontuacao,
                contexto.pontuacaoMinima(), contexto.criterios().isEmpty());
        boolean aprovado = !faltaObrigatorio && pontuacao >= contexto.pontuacaoMinima();
        return aprovado
                ? ResultadoDaAnalise.aprovada(pontuacao, motivo, descricoesAtendidas, descricoesNaoAtendidas)
                : ResultadoDaAnalise.reprovada(pontuacao, motivo, descricoesAtendidas, descricoesNaoAtendidas);
    }

    private Set<Long> idsAtendidos(ContextoDaAnalise contexto) {
        List<ContextoDaAnalise.Criterio> criterios = contexto.criterios();
        if (criterios.isEmpty()) {
            // Sem criterio nao ha o que consultar, e um values vazio nem seria
            // SQL valido. A ausencia do curriculo deixa de ser detectavel aqui,
            // mas tambem deixa de importar: nada seria lido dele.
            return Set.of();
        }

        JdbcClient.StatementSpec consulta = jdbc.sql(SQL_ATENDIMENTO.formatted(linhasDeCriterios(criterios.size())))
                .param("curriculo", contexto.curriculoId());
        for (int posicao = 0; posicao < criterios.size(); posicao++) {
            consulta = consulta.param("id" + posicao, criterios.get(posicao).id())
                    .param("expressao" + posicao, criterios.get(posicao).expressao());
        }
        List<Atendimento> linhas = consulta
                .query((registro, numero) -> new Atendimento(registro.getLong("id"), registro.getBoolean("atende")))
                .list();

        if (linhas.size() != criterios.size()) {
            throw new IllegalStateException("curriculo " + contexto.curriculoId()
                    + " da candidatura " + contexto.candidaturaId()
                    + " nao esta mais no banco; analise abortada para nao reprovar por falha nossa");
        }

        Set<Long> atendidos = new HashSet<>();
        for (Atendimento linha : linhas) {
            if (linha.atende()) {
                atendidos.add(linha.id());
            }
        }
        return atendidos;
    }

    private static String linhasDeCriterios(int quantidade) {
        StringJoiner linhas = new StringJoiner(", ");
        for (int posicao = 0; posicao < quantidade; posicao++) {
            // So a primeira linha declara os tipos; as outras os herdam da
            // coluna, que e como o PostgreSQL resolve o tipo de um values.
            linhas.add(posicao == 0
                    ? "(cast(:id0 as bigint), cast(:expressao0 as text))"
                    : "(:id" + posicao + ", :expressao" + posicao + ")");
        }
        return linhas.toString();
    }

    private static int pontuacao(int pesoAtendido, int pesoTotal) {
        if (pesoTotal <= 0) {
            // Vaga sem criterio nenhum (a fatia de vagas permite publicar
            // assim): nada deixou de ser atendido, entao a nota e cheia.
            return PONTUACAO_MAXIMA;
        }
        return (int) Math.round((double) pesoAtendido * PONTUACAO_MAXIMA / pesoTotal);
    }

    /** O texto que o candidato le no e-mail: em portugues, com acentuacao. */
    private static String motivo(List<String> atendidos,
                                 List<String> naoAtendidos,
                                 int pontuacao,
                                 int pontuacaoMinima,
                                 boolean semCriterios) {
        StringBuilder motivo = new StringBuilder();
        if (semCriterios) {
            // A triagem automatica so filtra o que o RH pediu para filtrar.
            // Sem criterio nenhum, reprovar seria inventar um motivo que nao
            // existe, e a decisao volta para as maos de quem publicou a vaga.
            motivo.append("A vaga não definiu critérios de aceite, então não havia o que conferir no currículo. ");
        } else {
            if (!atendidos.isEmpty()) {
                motivo.append("Critérios atendidos: ").append(String.join(", ", atendidos)).append(". ");
            }
            if (!naoAtendidos.isEmpty()) {
                motivo.append("Critérios não atendidos: ").append(String.join(", ", naoAtendidos)).append(". ");
            }
        }
        return motivo.append("Pontuação %d de %d (mínimo da vaga: %d)."
                .formatted(pontuacao, PONTUACAO_MAXIMA, pontuacaoMinima)).toString();
    }

    /** Uma linha da consulta: o criterio e se o curriculo o atende. */
    private record Atendimento(long id, boolean atende) {
    }
}
