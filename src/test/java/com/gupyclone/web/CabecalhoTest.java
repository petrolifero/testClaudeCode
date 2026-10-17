package com.gupyclone.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * O canto superior direito, que e o que o enunciado descreve em detalhe:
 * deslogado, tres pessoas; logado, um botao de sair e nenhuma forma de trocar
 * de pessoa sem sair antes.
 */
class CabecalhoTest extends TesteDeInterface {

    @Test
    @DisplayName("deslogado: as tres pessoas no canto, e nenhum botao de sair")
    void deslogadoMostraAsTresPessoas() {
        var pagina = navegador().abrir("/").html();

        var entrar = pagina.select("form[action*=/auth/entrar/]");
        assertThat(entrar).hasSize(3);
        assertThat(entrar.stream().map(form -> form.attr("action")).toList())
                .anyMatch(acao -> acao.endsWith("/ana"))
                .anyMatch(acao -> acao.endsWith("/carlos"))
                .anyMatch(acao -> acao.endsWith("/joao"));
        assertThat(pagina.select("form[action*=/auth/sair]")).isEmpty();
    }

    @Test
    @DisplayName("logado: um botao de sair, e ZERO formularios de entrar")
    void logadoMostraApenasSair() {
        var navegador = navegador();
        navegador.abrir("/");

        navegador.postar("/auth/entrar/carlos");
        var pagina = navegador.abrir("/").html();

        assertThat(pagina.select("form[action*=/auth/sair]")).hasSize(1);
        assertThat(pagina.select("form[action*=/auth/entrar/]"))
                .as("a opcao de entrar como outro tem de SUMIR do HTML, nao apenas ficar escondida")
                .isEmpty();
        assertThat(pagina.selectFirst(".quem-nome").text()).isEqualTo("Carlos Pereira");
        assertThat(pagina.selectFirst(".quem-papel").text()).isEqualTo("Candidato");
    }

    @Test
    @DisplayName("Ana entra como RH e o cabecalho diz isso")
    void anaEntraComoRh() {
        var navegador = navegador();
        navegador.abrir("/");

        navegador.postar("/auth/entrar/ana");
        var pagina = navegador.abrir("/").html();

        assertThat(pagina.selectFirst(".quem-nome").text()).isEqualTo("Ana Souza");
        assertThat(pagina.selectFirst(".quem-papel").text()).isEqualTo("RH");
    }

    @Test
    @DisplayName("deslogado so acessa a pagina inicial")
    void deslogadoSoAcessaAInicial() {
        var navegador = navegador();

        assertThat(navegador.abrir("/").status()).isEqualTo(200);
        for (String protegida : new String[]{"/vagas", "/minhas-candidaturas", "/rh/vagas", "/rh/vagas/nova"}) {
            var resposta = navegador.abrir(protegida);
            assertThat(resposta.status()).as(protegida).isEqualTo(302);
            assertThat(resposta.localizacao()).as(protegida).endsWith("/?aviso=entre-para-continuar");
        }
    }

    @Test
    @DisplayName("sair mata o cookie: as paginas protegidas voltam a redirecionar")
    void sairMataOCookie() {
        var navegador = navegador();
        navegador.abrir("/");
        navegador.postar("/auth/entrar/carlos");
        assertThat(navegador.abrir("/vagas").status()).isEqualTo(200);

        navegador.postar("/auth/sair");

        var resposta = navegador.abrir("/vagas");
        assertThat(resposta.status()).isEqualTo(302);
        assertThat(resposta.localizacao()).endsWith("/?aviso=entre-para-continuar");
    }

    @Test
    @DisplayName("nao troca de pessoa sem sair antes")
    void naoTrocaDePessoaSemSair() {
        var navegador = navegador();
        navegador.abrir("/");
        navegador.postar("/auth/entrar/carlos");

        // Mesmo forjando o POST que a interface nao oferece, o servidor recusa.
        var tentativa = navegador.postar("/auth/entrar/joao");

        assertThat(tentativa.status()).isEqualTo(302);
        assertThat(tentativa.localizacao()).endsWith("/?aviso=saia-antes-de-entrar-como-outra-pessoa");
        assertThat(navegador.abrir("/").html().selectFirst(".quem-nome").text())
                .as("continua sendo Carlos")
                .isEqualTo("Carlos Pereira");
    }

    @Test
    @DisplayName("o aviso da querystring aparece, e so o conhecido")
    void avisoConhecido() {
        var navegador = navegador();

        assertThat(navegador.abrir("/?aviso=entre-para-continuar").html().select(".recado.aviso").text())
                .contains("Entre como uma das pessoas");
        assertThat(navegador.abrir("/?aviso=qualquer-coisa-injetada").html().select(".recado.aviso"))
                .as("chave desconhecida nao vira texto na pagina")
                .isEmpty();
    }

    @Test
    @DisplayName("cada papel ve apenas o seu menu")
    void menuPorPapel() {
        var deCarlos = navegador();
        deCarlos.abrir("/");
        deCarlos.postar("/auth/entrar/carlos");
        var paginaDoCarlos = deCarlos.abrir("/").html();
        assertThat(paginaDoCarlos.select("nav.menu a[href=/minhas-candidaturas]")).hasSize(1);
        assertThat(paginaDoCarlos.select("nav.menu a[href=/rh/vagas]")).isEmpty();

        var deAna = navegador();
        deAna.abrir("/");
        deAna.postar("/auth/entrar/ana");
        var paginaDaAna = deAna.abrir("/").html();
        assertThat(paginaDaAna.select("nav.menu a[href=/rh/vagas]")).hasSize(1);
        assertThat(paginaDaAna.select("nav.menu a[href=/minhas-candidaturas]")).isEmpty();
    }
}
