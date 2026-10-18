package com.gupyclone.triagem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gupyclone.candidaturas.Candidatura;
import com.gupyclone.suporte.FabricaDePdf;
import com.gupyclone.vagas.ServicoDeVagas.NovoCriterio;
import com.gupyclone.vagas.Vaga;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Prova da analise textual contra o PostgreSQL de verdade: e a configuracao
 * {@code portugues_sem_acento} e o {@code websearch_to_tsquery} do banco que
 * decidem, nao uma comparacao de strings em Java.
 */
class AnalisadorFullTextSearchTest extends TesteDeTriagem {

    @Autowired
    private AnalisadorFullTextSearch analisador;

    @Test
    @DisplayName("o nome e o que a propriedade gupy.triagem.estrategia espera")
    void nomeDaEstrategia() {
        assertThat(analisador.nome()).isEqualTo("full-text-search");
    }

    @Test
    @DisplayName("curriculo que atende tudo pontua 100 e e aprovado")
    void atendeTodosOsCriterios() {
        Vaga vaga = publicarVagaDeExemplo(60);
        Candidatura candidatura = candidatar(carlos, vaga.getId(), PDF_QUE_ATENDE);

        ResultadoDaAnalise resultado = analisador.analisar(contextoDe(candidatura));

        assertThat(resultado.aprovado()).isTrue();
        assertThat(resultado.pontuacao()).isEqualTo(100);
        assertThat(resultado.criteriosAtendidos()).containsExactlyInAnyOrder("Java", "PostgreSQL", "Inglês");
        assertThat(resultado.criteriosNaoAtendidos()).isEmpty();
        assertThat(resultado.motivo()).contains("Java").contains("100");
    }

    @Test
    @DisplayName("curriculo de outra carreira e reprovado, e o motivo diz o que faltou")
    void naoAtendeNenhumCriterio() {
        Vaga vaga = publicarVagaDeExemplo(60);
        Candidatura candidatura = candidatar(carlos, vaga.getId(), PDF_QUE_NAO_ATENDE);

        ResultadoDaAnalise resultado = analisador.analisar(contextoDe(candidatura));

        assertThat(resultado.aprovado()).isFalse();
        assertThat(resultado.pontuacao()).isZero();
        assertThat(resultado.criteriosAtendidos()).isEmpty();
        assertThat(resultado.criteriosNaoAtendidos()).containsExactlyInAnyOrder("Java", "PostgreSQL", "Inglês");
        // O texto vai no e-mail do candidato: tem que estar em portugues de
        // gente, com acentuacao e dizendo o que faltou.
        assertThat(resultado.motivo())
                .contains("não atendidos")
                .contains("Java")
                .contains("obrigatório");
    }

    @Test
    @DisplayName("a pontuacao e a soma dos pesos atendidos sobre o peso total")
    void pontuacaoSomaPesos() {
        Vaga vaga = publicarVagaDeExemplo(0);
        // Atende Java (50) e inglês (20), nao atende PostgreSQL (30): 70 de 100.
        Candidatura candidatura = candidatar(carlos, vaga.getId(), FabricaDePdf.comCamadaDeTexto(
                "Carlos Pereira",
                "Desenvolvedora Java com Spring Boot e Oracle",
                "Inglês fluente, sem experiência com bancos livres"));

        ResultadoDaAnalise resultado = analisador.analisar(contextoDe(candidatura));

        assertThat(resultado.pontuacao()).isEqualTo(70);
        assertThat(resultado.criteriosAtendidos()).containsExactlyInAnyOrder("Java", "Inglês");
        assertThat(resultado.criteriosNaoAtendidos()).containsExactly("PostgreSQL");
        assertThat(resultado.aprovado()).isTrue();
    }

    @Test
    @DisplayName("criterio obrigatorio nao atendido reprova mesmo com pontuacao alta")
    void obrigatorioNaoAtendidoReprovaComPontuacaoAlta() {
        Vaga vaga = publicarVaga("Pessoa de Dados", 50,
                new NovoCriterio("Python", "python", 90, false),
                new NovoCriterio("Certificação AWS", "aws", 10, true));
        Candidatura candidatura = candidatar(carlos, vaga.getId(), FabricaDePdf.comCamadaDeTexto(
                "Carlos Pereira",
                "Engenheiro de dados com Python, Pandas e Airflow",
                "Experiencia com data warehouse on premise"));

        ResultadoDaAnalise resultado = analisador.analisar(contextoDe(candidatura));

        assertThat(resultado.pontuacao()).isEqualTo(90);
        assertThat(resultado.aprovado()).as("90 passa da minima, mas o obrigatorio nao foi atendido").isFalse();
        assertThat(resultado.criteriosNaoAtendidos()).containsExactly("Certificação AWS");
        assertThat(resultado.motivo()).contains("Certificação AWS").contains("obrigatório");
    }

    @Test
    @DisplayName("pontuacao abaixo da minima reprova mesmo com todos os obrigatorios atendidos")
    void abaixoDaMinimaReprova() {
        Vaga vaga = publicarVagaDeExemplo(80);
        Candidatura candidatura = candidatar(carlos, vaga.getId(), FabricaDePdf.comCamadaDeTexto(
                "Carlos Pereira",
                "Desenvolvedora Java com Spring Boot e Oracle",
                "Experiencia com integracao de sistemas legados"));

        ResultadoDaAnalise resultado = analisador.analisar(contextoDe(candidatura));

        assertThat(resultado.pontuacao()).isEqualTo(50);
        assertThat(resultado.aprovado()).isFalse();
        assertThat(resultado.motivo()).contains("80");
    }

    @Test
    @DisplayName("acento e caixa nao importam: 'pos-graduacao' casa com 'Pós-Graduação'")
    void acentoECaixaNaoImportam() {
        Vaga vaga = publicarVaga("Pessoa Pesquisadora", 0,
                new NovoCriterio("Pós-graduação", "pos-graduacao", 50, true),
                new NovoCriterio("Java", "JAVA", 50, true));
        Candidatura candidatura = candidatar(carlos, vaga.getId(), PDF_QUE_ATENDE);

        ResultadoDaAnalise resultado = analisador.analisar(contextoDe(candidatura));

        assertThat(resultado.criteriosAtendidos()).containsExactlyInAnyOrder("Pós-graduação", "Java");
        assertThat(resultado.pontuacao()).isEqualTo(100);
    }

    @Test
    @DisplayName("e o inverso tambem: criterio acentuado casa com curriculo sem acento")
    void criterioAcentuadoCasaComCurriculoSemAcento() {
        Vaga vaga = publicarVaga("Pessoa Pesquisadora", 0,
                new NovoCriterio("Pós-graduação", "Pós-Graduação", 100, true));
        Candidatura candidatura = candidatar(carlos, vaga.getId(), FabricaDePdf.comCamadaDeTexto(
                "Joao Lima",
                "Pos-graduacao em computacao pela universidade federal",
                "Atuacao em pesquisa aplicada"));

        ResultadoDaAnalise resultado = analisador.analisar(contextoDe(candidatura));

        assertThat(resultado.criteriosAtendidos()).containsExactly("Pós-graduação");
        assertThat(resultado.aprovado()).isTrue();
    }

    @Test
    @DisplayName("expressao com 'or' e com negacao '-termo' funciona e nao estoura")
    void expressaoComOrEComNegacao() {
        Vaga vaga = publicarVaga("Pessoa de Backend", 0,
                new NovoCriterio("JVM", "java or kotlin or scala", 40, true),
                new NovoCriterio("Sem PHP", "-php", 30, false),
                new NovoCriterio("Frase exata", "\"spring boot\"", 30, false));
        Candidatura candidatura = candidatar(carlos, vaga.getId(), PDF_QUE_ATENDE);

        ResultadoDaAnalise resultado = analisador.analisar(contextoDe(candidatura));

        assertThat(resultado.criteriosAtendidos()).containsExactlyInAnyOrder("JVM", "Sem PHP", "Frase exata");
        assertThat(resultado.pontuacao()).isEqualTo(100);
    }

    @Test
    @DisplayName("a negacao reprova quem tem o termo proibido")
    void negacaoReprovaQuemTemOTermo() {
        Vaga vaga = publicarVaga("Pessoa de Backend", 0,
                new NovoCriterio("Sem PHP", "-php", 100, true));
        Candidatura candidatura = candidatar(carlos, vaga.getId(), FabricaDePdf.comCamadaDeTexto(
                "Carlos Pereira",
                "Desenvolvedor PHP com Laravel e MySQL",
                "Experiencia com WordPress"));

        ResultadoDaAnalise resultado = analisador.analisar(contextoDe(candidatura));

        assertThat(resultado.criteriosNaoAtendidos()).containsExactly("Sem PHP");
        assertThat(resultado.aprovado()).isFalse();
    }

    @Test
    @DisplayName("texto livre com pontuacao estranha nao estoura erro de sintaxe")
    void expressaoLivreNaoEstoura() {
        // to_tsquery estouraria com isso; websearch_to_tsquery e justamente a
        // funcao que aceita o que uma pessoa digita.
        Vaga vaga = publicarVaga("Pessoa de Backend", 0,
                new NovoCriterio("Expressão torta", "java & | ! ( postgres :* ", 100, false));
        Candidatura candidatura = candidatar(carlos, vaga.getId(), PDF_QUE_ATENDE);

        ResultadoDaAnalise resultado = analisador.analisar(contextoDe(candidatura));

        // isBetween(0, 100) sozinho seria VACUO: o construtor de
        // ResultadoDaAnalise ja recusa valor fora dessa faixa, logo nenhum
        // resultado construivel poderia falhar essa asserticao.
        //
        // O comportamento real, que vale fixar: a pontuacao do lixo e
        // descartada e as palavras que sobram ficam ligadas por E. Aqui sobram
        // "java" E "postgres", e "postgres" NAO casa com "PostgreSQL" depois da
        // radicalizacao -- por isso o criterio nao e atendido. O ponto do teste
        // e que isso vira um veredito, nao um erro de sintaxe.
        assertThat(resultado.pontuacao()).isZero();
        assertThat(resultado.criteriosNaoAtendidos()).containsExactly("Expressão torta");
    }

    @Test
    @DisplayName("pontuacao solta na expressao e descartada, e o termo que sobra casa")
    void pontuacaoNaExpressaoEDescartada() {
        Vaga vaga = publicarVaga("Pessoa de Backend", 0,
                new NovoCriterio("Java mesmo com lixo", "java & | ! ( :* ", 100, false));
        Candidatura candidatura = candidatar(carlos, vaga.getId(), PDF_QUE_ATENDE);

        ResultadoDaAnalise resultado = analisador.analisar(contextoDe(candidatura));

        assertThat(resultado.pontuacao()).isEqualTo(100);
        assertThat(resultado.criteriosAtendidos()).containsExactly("Java mesmo com lixo");
    }

    @Test
    @DisplayName("vaga sem criterio nenhum aprova: nao ha o que reprovar")
    void vagaSemCriterioAprova() {
        // A fatia de vagas permite publicar sem criterio. A triagem so filtra
        // o que o RH pediu para filtrar, e sem pedido nenhum reprovar seria
        // inventar um motivo que nao existe.
        Vaga vaga = publicarVaga("Pessoa Generalista", 0);
        Candidatura candidatura = candidatar(carlos, vaga.getId(), PDF_QUE_NAO_ATENDE);

        ResultadoDaAnalise resultado = analisador.analisar(contextoDe(candidatura));

        assertThat(resultado.aprovado()).isTrue();
        assertThat(resultado.pontuacao()).isEqualTo(100);
        assertThat(resultado.criteriosAtendidos()).isEmpty();
        assertThat(resultado.criteriosNaoAtendidos()).isEmpty();
        assertThat(resultado.motivo()).contains("não definiu critérios");
    }

    @Test
    @DisplayName("curriculo que nao existe mais e erro, nao reprovacao silenciosa")
    void curriculoInexistenteNaoReprovaEmSilencio() {
        Vaga vaga = publicarVagaDeExemplo(60);
        Candidatura candidatura = candidatar(carlos, vaga.getId(), PDF_QUE_ATENDE);
        ContextoDaAnalise contexto = contextoDe(candidatura);
        ContextoDaAnalise semCurriculo = new ContextoDaAnalise(contexto.candidaturaId(),
                contexto.vagaId(),
                contexto.tituloDaVaga(),
                contexto.descricaoDaVaga(),
                contexto.pontuacaoMinima(),
                9_999_999L,
                contexto.textoDoCurriculo(),
                contexto.criterios());

        // Sem essa checagem o join nao acharia o curriculo, nenhum criterio
        // seria atendido e o candidato levaria uma reprovacao por uma falha
        // nossa.
        assertThatThrownBy(() -> analisador.analisar(semCurriculo))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("9999999");
    }
}
