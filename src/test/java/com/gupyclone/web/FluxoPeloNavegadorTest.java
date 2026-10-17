package com.gupyclone.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.gupyclone.candidaturas.CandidaturaRepository;
import com.gupyclone.curriculos.CurriculoRepository;
import com.gupyclone.suporte.FabricaDePdf;
import com.gupyclone.triagem.ProcessadorDeAnalises;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * O caminho completo pelo navegador: Ana publica, Carlos se candidata enviando
 * um PDF de verdade, a triagem decide, e os dois lados veem o resultado.
 *
 * <p>Tudo por HTTP com cookie e token CSRF reais, nao por
 * {@code Authorization: Bearer}: e o caminho da pessoa que esta usando o site.
 */
class FluxoPeloNavegadorTest extends TesteDeInterface {

    @Autowired
    private ProcessadorDeAnalises processador;

    @Autowired
    private CandidaturaRepository candidaturas;

    @Autowired
    private CurriculoRepository curriculos;

    private static final byte[] CURRICULO_QUE_ATENDE = FabricaDePdf.comCamadaDeTexto(
            "Carlos Pereira",
            "Desenvolvedor Java com Spring Boot",
            "Experiencia com PostgreSQL e Docker",
            "Pos-graduacao em Engenharia de Software");

    @Test
    @DisplayName("Ana publica, Carlos se candidata, a triagem decide e os dois veem o resultado")
    void fluxoCompleto() {
        // --- Ana, do RH, publica a vaga pelo formulario ---
        var daAna = navegador();
        daAna.abrir("/");
        daAna.postar("/auth/entrar/ana");
        assertThat(daAna.abrir("/rh/vagas/nova").status()).isEqualTo(200);

        var publicacao = daAna.postar("/rh/vagas", List.of(
                Map.entry("titulo", "Pessoa Desenvolvedora Java"),
                Map.entry("descricao", "Time de plataforma, trabalho remoto."),
                Map.entry("pontuacaoMinima", "50"),
                Map.entry("criterioDescricao", "Java"),
                Map.entry("criterioExpressao", "java"),
                Map.entry("criterioPeso", "60"),
                Map.entry("criterioObrigatorio", "true"),
                Map.entry("criterioDescricao", "PostgreSQL"),
                Map.entry("criterioExpressao", "postgresql or postgres"),
                Map.entry("criterioPeso", "40"),
                Map.entry("criterioObrigatorio", "false")));
        assertThat(publicacao.status()).isEqualTo(302);

        var vagasDoRh = daAna.seguir(publicacao).html();
        assertThat(vagasDoRh.select(".recado.sucesso").text()).contains("publicada");
        assertThat(vagasDoRh.select(".cartao h2").text()).contains("Pessoa Desenvolvedora Java");
        daAna.postar("/auth/sair");

        // --- Carlos ve a vaga e envia o curriculo ---
        var doCarlos = navegador();
        doCarlos.abrir("/");
        doCarlos.postar("/auth/entrar/carlos");

        var lista = doCarlos.abrir("/vagas").html();
        assertThat(lista.select(".lista-vagas .cartao")).hasSize(1);
        String caminhoDaVaga = lista.selectFirst(".lista-vagas .cartao h2 a").attr("href");

        var detalhe = doCarlos.abrir(caminhoDaVaga).html();
        assertThat(detalhe.select(".criterios tbody tr")).hasSize(2);
        assertThat(detalhe.select(".envio form")).as("candidato ve o formulario de envio").hasSize(1);

        var envio = doCarlos.enviarArquivo(caminhoDaVaga + "/candidatar", "arquivo",
                "curriculo-carlos.pdf", CURRICULO_QUE_ATENDE);
        assertThat(envio.status()).as("o POST multipart passa pelo CSRF").isEqualTo(302);
        assertThat(doCarlos.seguir(envio).html().select(".recado.sucesso").text())
                .contains("Currículo enviado");
        assertThat(candidaturas.count()).isEqualTo(1);

        // --- a triagem decide (o agendador esta desligado nos testes) ---
        assertThat(processador.processarUmLote()).isEqualTo(1);

        // --- Carlos ve o resultado ---
        var minhas = doCarlos.abrir("/minhas-candidaturas").html();
        assertThat(minhas.select(".candidaturas tbody tr")).hasSize(1);
        assertThat(minhas.selectFirst(".selo").text()).isEqualTo("APROVADA");
        assertThat(minhas.selectFirst("td.motivo").text()).isNotBlank();
        assertThat(minhas.select(".candidaturas tbody tr").text()).contains("texto");
        doCarlos.postar("/auth/sair");

        // --- Ana ve a candidatura com pontuacao e motivo ---
        var deNovoDaAna = navegador();
        deNovoDaAna.abrir("/");
        deNovoDaAna.postar("/auth/entrar/ana");
        var acompanhamento = deNovoDaAna.abrir("/rh/vagas").html();

        assertThat(acompanhamento.select(".candidaturas tbody tr")).hasSize(1);
        var linha = acompanhamento.selectFirst(".candidaturas tbody tr");
        assertThat(linha.text()).contains("Carlos Pereira").contains("APROVADA");
        assertThat(linha.selectFirst("td.motivo").text()).isNotBlank();
    }

    @Test
    @DisplayName("curriculo digitalizado (PDF imagem) chega ate a decisao pela interface")
    void curriculoDigitalizadoPelaInterface() {
        Long vagaId = publicarVagaSimples();

        var doJoao = navegador();
        doJoao.abrir("/");
        doJoao.postar("/auth/entrar/joao");
        var envio = doJoao.enviarArquivo("/vagas/" + vagaId + "/candidatar", "arquivo", "digitalizado.pdf",
                FabricaDePdf.somenteImagem("Joao Lima", "Desenvolvedor Java com Spring Boot"));
        assertThat(envio.status()).isEqualTo(302);

        processador.processarUmLote();

        var minhas = doJoao.abrir("/minhas-candidaturas").html();
        assertThat(minhas.select(".candidaturas tbody tr").text())
                .as("a interface mostra que o texto veio de OCR")
                .contains("digitalizado (OCR)");
    }

    @Test
    @DisplayName("arquivo que nao e PDF volta para a vaga com recado, e nada e gravado")
    void arquivoInvalidoMostraRecado() {
        Long vagaId = publicarVagaSimples();

        var doCarlos = navegador();
        doCarlos.abrir("/");
        doCarlos.postar("/auth/entrar/carlos");
        var envio = doCarlos.enviarArquivo("/vagas/" + vagaId + "/candidatar", "arquivo", "foto.pdf",
                "isto nao e um PDF".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertThat(envio.status()).isEqualTo(302);
        var pagina = doCarlos.seguir(envio).html();
        assertThat(pagina.select(".recado.erro").text()).isNotBlank();
        assertThat(pagina.select(".envio form")).as("o formulario continua disponivel").hasSize(1);
        assertThat(candidaturas.count()).isZero();
        assertThat(curriculos.count()).isZero();
    }

    @Test
    @DisplayName("candidatar-se duas vezes pela interface mostra o andamento, nao duplica")
    void naoDuplicaPelaInterface() {
        Long vagaId = publicarVagaSimples();

        var doCarlos = navegador();
        doCarlos.abrir("/");
        doCarlos.postar("/auth/entrar/carlos");
        doCarlos.abrir("/vagas/" + vagaId);
        doCarlos.enviarArquivo("/vagas/" + vagaId + "/candidatar", "arquivo", "c.pdf", CURRICULO_QUE_ATENDE);

        // Com a candidatura feita, a pagina troca o formulario pelo andamento.
        var depois = doCarlos.abrir("/vagas/" + vagaId).html();
        assertThat(depois.select(".envio form")).isEmpty();
        assertThat(depois.select(".andamento")).hasSize(1);

        // Mesmo forjando o envio, o servidor recusa e nada duplica.
        var segundoEnvio = doCarlos.enviarArquivo("/vagas/" + vagaId + "/candidatar", "arquivo", "c.pdf",
                CURRICULO_QUE_ATENDE);
        assertThat(doCarlos.seguir(segundoEnvio).html().select(".recado.erro").text()).isNotBlank();
        assertThat(candidaturas.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("cada papel so entra no que e seu")
    void autorizacaoPorPapelNasPaginas() {
        var doCarlos = navegador();
        doCarlos.abrir("/");
        doCarlos.postar("/auth/entrar/carlos");
        assertThat(doCarlos.abrir("/rh/vagas").status()).isEqualTo(403);
        assertThat(doCarlos.abrir("/rh/vagas/nova").status()).isEqualTo(403);

        var daAna = navegador();
        daAna.abrir("/");
        daAna.postar("/auth/entrar/ana");
        assertThat(daAna.abrir("/minhas-candidaturas").status()).isEqualTo(403);
    }

    /** Publica uma vaga simples como Ana e devolve o id. */
    private Long publicarVagaSimples() {
        var daAna = navegador();
        daAna.abrir("/");
        daAna.postar("/auth/entrar/ana");
        var publicacao = daAna.postar("/rh/vagas", List.of(
                Map.entry("titulo", "Pessoa Desenvolvedora Java"),
                Map.entry("descricao", "Time de plataforma."),
                Map.entry("pontuacaoMinima", "40"),
                Map.entry("criterioDescricao", "Java"),
                Map.entry("criterioExpressao", "java"),
                Map.entry("criterioPeso", "100"),
                Map.entry("criterioObrigatorio", "true")));
        assertThat(publicacao.status()).isEqualTo(302);
        var cartao = daAna.seguir(publicacao).html().selectFirst(".cartao h2 a");
        return Long.valueOf(cartao.attr("href").replaceAll(".*/", ""));
    }
}
