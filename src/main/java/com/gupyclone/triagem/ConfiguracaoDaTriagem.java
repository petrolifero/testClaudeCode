package com.gupyclone.triagem;

import com.gupyclone.config.PropriedadesTriagem;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Escolhe, no boot, qual analisador a triagem vai usar.
 *
 * <p>E aqui que mora o "desacoplada, com interface para podermos trocar no
 * futuro": o processador da fila depende de {@link AnalisadorDeCandidatura} e
 * nunca de uma implementacao, e acrescentar uma terceira estrategia (vetores,
 * por exemplo) e escrever um {@code @Component} novo — nada mais neste arquivo.
 *
 * <p>A escolha falha o contexto quando a propriedade nao casa com nenhum
 * analisador. Cair no boot, dizendo o que existe, e muito melhor que as duas
 * alternativas: processar com um analisador escolhido por acaso, ou subir com a
 * fila de triagem parada em silencio.
 */
@Configuration
public class ConfiguracaoDaTriagem {

    private static final Logger log = LoggerFactory.getLogger(ConfiguracaoDaTriagem.class);

    /**
     * O analisador que {@code gupy.triagem.estrategia} nomeia.
     *
     * <p>{@code @Primary} porque as implementacoes tambem sao beans do mesmo
     * tipo: e esta marca que faz quem injeta {@link AnalisadorDeCandidatura}
     * receber o escolhido, e nao uma ambiguidade. A lista recebida nao inclui
     * este bean (o Spring exclui a auto-referencia ao resolver a colecao).
     */
    @Bean
    @Primary
    AnalisadorDeCandidatura analisadorDaEstrategia(List<AnalisadorDeCandidatura> disponiveis,
                                                   AnalisadorFullTextSearch busca,
                                                   PropriedadesTriagem propriedades) {
        AnalisadorDeCandidatura escolhido = disponiveis.stream()
                .filter(analisador -> analisador.nome().equals(propriedades.estrategia()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "gupy.triagem.estrategia nao casa com nenhum analisador: '" + propriedades.estrategia()
                                + "'. disponiveis: " + nomes(disponiveis)));
        log.info("triagem usando a estrategia '{}' ({})", escolhido.nome(), escolhido.getClass().getSimpleName());
        return comVetoDeObrigatorios(escolhido, busca);
    }

    /**
     * Envolve a estrategia escolhida no veto de criterios obrigatorios.
     *
     * <p>O analisador textual nao e envolvido porque ele proprio ja decide pela
     * mesma consulta: envolve-lo custaria uma ida ao banco por analise sem
     * mudar nenhum veredito. Qualquer outra estrategia, que decide por meio
     * influenciavel pelo texto do candidato, passa pelo veto.
     */
    private static AnalisadorDeCandidatura comVetoDeObrigatorios(AnalisadorDeCandidatura escolhido,
                                                                 AnalisadorFullTextSearch busca) {
        if (escolhido instanceof AnalisadorFullTextSearch) {
            return escolhido;
        }
        log.info("veto de criterios obrigatorios ativo sobre a estrategia '{}'", escolhido.nome());
        return new AnalisadorComVetoDeObrigatorios(escolhido, busca);
    }

    private static List<String> nomes(List<AnalisadorDeCandidatura> disponiveis) {
        return disponiveis.stream().map(AnalisadorDeCandidatura::nome).sorted().toList();
    }
}
