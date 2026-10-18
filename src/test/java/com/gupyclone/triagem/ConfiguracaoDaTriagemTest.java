package com.gupyclone.triagem;

import static org.assertj.core.api.Assertions.assertThat;

import com.gupyclone.config.PropriedadesTriagem;
import com.gupyclone.suporte.TesteDeIntegracao;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Prova da troca de estrategia: quem decide qual analisador roda e a
 * propriedade {@code gupy.triagem.estrategia}, e um valor que nao casa com
 * nenhum analisador derruba o contexto em vez de deixar a fila parada ou, pior,
 * escolher um analisador por sorteio.
 *
 * <p>Os casos de erro usam {@link ApplicationContextRunner} com os
 * analisadores <em>de verdade</em> (os beans desta aplicacao, injetados
 * abaixo): o que muda de um caso para o outro e so a propriedade.
 */
class ConfiguracaoDaTriagemTest extends TesteDeIntegracao {

    @Autowired
    private AnalisadorDeCandidatura escolhido;

    @Autowired
    private AnalisadorFullTextSearch textual;

    @Autowired
    private AnalisadorViaLlm viaLlm;

    @Autowired
    private PropriedadesTriagem propriedades;

    @Test
    @DisplayName("a suite roda com a estrategia textual, e e ela que a triagem recebe")
    void estrategiaPadraoEhTextual() {
        assertThat(propriedades.estrategia()).isEqualTo(AnalisadorFullTextSearch.NOME);
        assertThat(escolhido).isSameAs(textual);
    }

    @Test
    @DisplayName("com estrategia 'full-text-search' o escolhido e o analisador textual")
    void escolheOTextual() {
        contextoCom(AnalisadorFullTextSearch.NOME).run(contexto -> {
            assertThat(contexto).hasNotFailed();
            assertThat(contexto.getBean(AnalisadorDeCandidatura.class)).isSameAs(textual);
        });
    }

    @Test
    @DisplayName("com estrategia 'llm' o escolhido e o analisador por LLM, envolvido no veto de obrigatorios")
    void escolheODeLlm() {
        contextoCom(AnalisadorViaLlm.NOME).run(contexto -> {
            assertThat(contexto).hasNotFailed();
            AnalisadorDeCandidatura publicado = contexto.getBean(AnalisadorDeCandidatura.class);
            assertThat(publicado.nome()).isEqualTo(AnalisadorViaLlm.NOME);
            assertThat(publicado)
                    .as("estrategia que decide por meio influenciavel pelo candidato passa pelo veto")
                    .isInstanceOf(AnalisadorComVetoDeObrigatorios.class);
        });
    }

    @Test
    @DisplayName("o analisador textual NAO e envolvido pelo veto: ele ja decide pela mesma consulta")
    void textualNaoEEnvolvido() {
        contextoCom(AnalisadorFullTextSearch.NOME).run(contexto -> {
            assertThat(contexto).hasNotFailed();
            assertThat(contexto.getBean(AnalisadorDeCandidatura.class))
                    .isNotInstanceOf(AnalisadorComVetoDeObrigatorios.class);
        });
    }

    @Test
    @DisplayName("estrategia inexistente derruba o contexto dizendo quais existem")
    void estrategiaInexistenteDerrubaOContexto() {
        contextoCom("busca-por-tarot").run(contexto -> {
            assertThat(contexto).hasFailed();
            assertThat(contexto.getStartupFailure())
                    .rootCause()
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("busca-por-tarot")
                    .hasMessageContaining(AnalisadorFullTextSearch.NOME)
                    .hasMessageContaining(AnalisadorViaLlm.NOME);
        });
    }

    private ApplicationContextRunner contextoCom(String estrategia) {
        return new ApplicationContextRunner()
                .withBean("analisadorFullTextSearch", AnalisadorDeCandidatura.class, () -> textual)
                .withBean("analisadorViaLlm", AnalisadorDeCandidatura.class, () -> viaLlm)
                .withBean(PropriedadesTriagem.class, () -> propriedadesCom(estrategia))
                .withUserConfiguration(ConfiguracaoDaTriagem.class);
    }

    private PropriedadesTriagem propriedadesCom(String estrategia) {
        return new PropriedadesTriagem(estrategia,
                propriedades.intervalo(),
                propriedades.lote(),
                propriedades.maxTentativas(),
                propriedades.esperaAposFalha(),
                propriedades.lease(),
                propriedades.quarentena());
    }
}
