package com.gupyclone.arquitetura;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Fixa a regra central da refatoracao de agregados: <em>dentro de um agregado,
 * referencia por objeto; entre agregados, referencia por id, e travessia so por
 * porta do modulo dono.</em>
 *
 * <p>Existe porque o defeito que esta refatoracao desfez nao foi encontrado por
 * teste nenhum: foi preciso uma pessoa estranhar um {@code Hibernate.initialize}
 * e puxar o fio ate descobrir que quatro agregados eram tratados como um. Nada
 * na suite avisava, e portanto nada impedia o problema de voltar.
 *
 * <p>Le o texto dos arquivos de {@code src/main/java} em vez de usar ArchUnit:
 * a biblioteca nao esta no {@code pom.xml}, e para as regras daqui a leitura
 * de texto basta. (Nao conte as regras em prosa: o numero derrapa a cada
 * regra nova, e ja derrapou.)
 */
class RegrasDeArquiteturaTest {

    private static final Path RAIZ = Path.of("src", "main", "java");

    /**
     * Pacotes de fora do grafo de dominio: {@code comum} e {@code config} sao
     * base de que todo mundo depende, {@code seguranca} e a borda de
     * autenticacao e {@code web} e a borda que consome todos. Ciclo entre eles e
     * os de dominio nao e sinal de agregado mal dividido.
     */
    private static final Set<String> FORA_DO_DOMINIO = Set.of("comum", "config", "seguranca", "web");

    /**
     * Divida conhecida da regra de repositorio, registrada arquivo por arquivo
     * para que uma violacao <em>nova</em> ainda falhe. O teste tambem falha se
     * uma entrada daqui parar de ser violacao: quando alguem consertar, tem de
     * apagar a linha.
     */
    private static final Map<String, String> DIVIDA_DE_REPOSITORIO_CRUZADO = Map.of(
            "seguranca/ServicoDePersonas.java",
            "resolve persona por chave e lista todas; a porta ConsultaDePessoas, "
                    + "congelada no contrato da refatoracao, le somente por id");

    /**
     * Quem pode montar a resposta <em>completa</em> de vaga, que carrega a
     * expressao de busca dos criterios. A expressao e o criterio de triagem em
     * forma executavel: entregue ao candidato, diria o que escrever no
     * curriculo para passar.
     *
     * <p>Nao da para provar posse estaticamente, entao a regra nao finge: ela
     * obriga todo ponto de chamada a estar declarado aqui, com o motivo pelo
     * qual quem chama ja e o dono. Um ponto novo falha o teste e tem de se
     * justificar. E o teste tambem falha se uma entrada daqui deixar de
     * existir, para a lista nao proteger um chamador que ja foi embora.
     *
     * <p>A alternativa segura e {@code paraQuemPergunta(...)}, que apaga a
     * expressao de quem nao e o RH dono.
     */
    private static final Map<String, String> PODE_MONTAR_RESPOSTA_COMPLETA = Map.of(
            "vagas/VagaController.java",
            "resposta do POST que acabou de publicar: quem publicou e o dono, por construcao",
            "web/PaginaDoRhController.java",
            "lista vagas.doRecrutador(usuario.entidadeId()): por consulta, sao as vagas do proprio");

    private static final Pattern MONTAGEM_COMPLETA = Pattern.compile("\\.completas?\\s*\\(");

    private static final Pattern IMPORT_INTERNO =
            Pattern.compile("^import\\s+(?:static\\s+)?com\\.gupyclone\\.([a-z]\\w*)\\.", Pattern.MULTILINE);

    private static final Pattern REPOSITORIO_QUALIFICADO =
            Pattern.compile("com\\.gupyclone\\.([a-z]\\w*)\\.(\\w*Repository)\\b");

    private static final Pattern TIPO_DO_CAMPO = Pattern.compile("([A-Z]\\w*)\\s+\\w+\\s*;\\s*$", Pattern.DOTALL);

    /** Um arquivo de producao, com o pacote simples dele e o texto cru. */
    private record ArquivoJava(Path caminho, String pacote, String texto) {

        /** Caminho curto, relativo a {@code com/gupyclone}, para mensagem de falha. */
        String nome() {
            return pacote.isEmpty() ? caminho.getFileName().toString()
                    : pacote + "/" + caminho.getFileName();
        }

        /** Pacote simples de cada tipo de {@code com.gupyclone} que este arquivo importa. */
        Map<String, String> pacotePorTipoImportado() {
            Map<String, String> porTipo = new HashMap<>();
            Matcher m = Pattern.compile("import\\s+com\\.gupyclone\\.([a-z]\\w*)\\.([A-Z]\\w*)").matcher(texto);
            while (m.find()) {
                porTipo.put(m.group(2), m.group(1));
            }
            return porTipo;
        }
    }

    private static List<ArquivoJava> arquivos;
    private static Set<String> pacotesDeDominio;

    @BeforeAll
    static void lerProducao() {
        try (Stream<Path> caminhos = Files.walk(RAIZ)) {
            arquivos = caminhos.filter(p -> p.toString().endsWith(".java"))
                    .sorted()
                    .map(RegrasDeArquiteturaTest::ler)
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("nao consegui ler " + RAIZ.toAbsolutePath(), e);
        }
        // Sem este guarda, um erro de caminho faria TODAS as regras passarem por
        // nao haver arquivo para violar nada — o pior defeito possivel aqui.
        if (arquivos.size() < 50) {
            throw new IllegalStateException("li apenas " + arquivos.size() + " arquivos em "
                    + RAIZ.toAbsolutePath() + "; as regras nao teriam o que afirmar");
        }
        pacotesDeDominio = arquivos.stream()
                .map(ArquivoJava::pacote)
                .filter(p -> !p.isEmpty() && !FORA_DO_DOMINIO.contains(p))
                .collect(java.util.stream.Collectors.toCollection(TreeSet::new));
    }

    private static ArquivoJava ler(Path caminho) {
        try {
            Path pai = caminho.getParent();
            String pacote = pai.endsWith(Path.of("com", "gupyclone")) ? "" : pai.getFileName().toString();
            return new ArquivoJava(caminho, pacote, Files.readString(caminho, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("nao consegui ler " + caminho, e);
        }
    }

    @Test
    @DisplayName("o inventario lido e o de producao: nenhuma regra pode passar por ler arquivo nenhum")
    void inventarioLido() {
        assertThat(arquivos)
                .as("arquivos .java lidos de %s", RAIZ)
                .hasSizeGreaterThanOrEqualTo(50);
        assertThat(pacotesDeDominio)
                .as("pacotes de dominio no grafo (fora dele: %s)", new TreeSet<>(FORA_DO_DOMINIO))
                .contains("candidaturas", "curriculos", "notificacoes", "pessoas", "triagem", "vagas");
    }

    @Test
    @DisplayName("Hibernate.initialize nao volta: ele e sintoma de agregado mal dividido, nao solucao")
    void nenhumHibernateInitialize() {
        List<String> culpados = arquivos.stream()
                .filter(a -> a.texto().contains("Hibernate.initialize"))
                .map(ArquivoJava::nome)
                .toList();

        assertThat(culpados)
                .as("""
                        Hibernate.initialize em producao. Ele aparece quando se tenta ler, \
                        de fora, uma associacao de outro agregado que chegou destacada \
                        (open-in-view e false). A travessia entre agregados e por PORTA do \
                        modulo dono (ConsultaDeVagas, ConsultaDePessoas, ConsultaDeCurriculos, \
                        Candidaturas) ou por projecao no proprio repositorio — nunca \
                        inicializando a associacao na mao. Vale tambem para comentario: \
                        prosa com o nome literal do metodo faz o grep do contrato acusar \
                        ocorrencia. Arquivo(s): %s""", culpados)
                .isEmpty();
    }

    @Test
    @DisplayName("nenhum ciclo entre pacotes de dominio: ciclo e a prova de que a fronteira do agregado esta no lugar errado")
    void nenhumCicloEntrePacotesDeDominio() {
        Map<String, Set<String>> grafo = grafoDeDominio();

        List<String> ciclo = procurarCiclo(grafo);

        if (ciclo != null) {
            fail("""
                    Ciclo entre pacotes de dominio: %s

                    Dois pacotes de dominio que se importam nao sao dois agregados, sao um \
                    agregado escrito em dois lugares. Quebre o ciclo invertendo a dependencia: \
                    o lado que precisa ser chamado DECLARA a porta e o outro a IMPLEMENTA \
                    (foi o que EnfileiramentoDeTriagem fez entre candidaturas e triagem). \
                    Grafo lido: %s""".formatted(String.join(" -> ", ciclo), grafo));
        }
    }

    @Test
    @DisplayName("repositorio nao sai do seu pacote: quem le de outro agregado usa a porta, e nao herda as escritas do JpaRepository")
    void nenhumRepositorioForaDoSeuPacote() {
        Map<String, Set<String>> porArquivo = new TreeMap<>();
        for (ArquivoJava a : arquivos) {
            Matcher m = REPOSITORIO_QUALIFICADO.matcher(a.texto());
            while (m.find()) {
                if (!m.group(1).equals(a.pacote())) {
                    porArquivo.computeIfAbsent(a.nome(), k -> new TreeSet<>())
                            .add("com.gupyclone." + m.group(1) + "." + m.group(2));
                }
            }
        }

        Map<String, Set<String>> novas = new TreeMap<>(porArquivo);
        novas.keySet().removeAll(DIVIDA_DE_REPOSITORIO_CRUZADO.keySet());
        assertThat(novas)
                .as("""
                        Repositorio de outro pacote. O repositorio e detalhe interno do \
                        modulo dono: use a PORTA dele (ConsultaDeVagas, ConsultaDePessoas, \
                        ConsultaDeCurriculos, Candidaturas), que devolve record de dados e \
                        nao traz as escritas do JpaRepository de carona. Se a porta nao serve, \
                        amplie a porta — nao o alcance do repositorio. Violacao(oes) nova(s): %s""",
                        novas)
                .isEmpty();

        // Divida que virou mentira tambem e defeito: quem consertar precisa
        // apagar a entrada, senao a lista protege uma violacao que voltou.
        Set<String> dividaQuitada = new TreeSet<>(DIVIDA_DE_REPOSITORIO_CRUZADO.keySet());
        dividaQuitada.removeAll(porArquivo.keySet());
        assertThat(dividaQuitada)
                .as("entrada(s) obsoleta(s) em DIVIDA_DE_REPOSITORIO_CRUZADO: "
                        + "o arquivo nao usa mais repositorio de outro pacote; apague a linha")
                .isEmpty();
    }

    @Test
    @DisplayName("entidade de outro pacote de dominio nao entra por @ManyToOne/@OneToOne: entre agregados a referencia e o id")
    void nenhumaEntidadeDeOutroPacotePorAssociacao() {
        Map<String, Set<String>> culpados = new TreeMap<>();
        for (ArquivoJava a : arquivos) {
            if (!pacotesDeDominio.contains(a.pacote())) {
                continue;
            }
            Map<String, String> pacotePorTipo = a.pacotePorTipoImportado();
            Matcher m = Pattern.compile("@(ManyToOne|OneToOne)\\b").matcher(a.texto());
            while (m.find()) {
                String tipo = tipoDoCampoApos(a.texto(), m.start());
                String pacoteDoTipo = tipo == null ? null : pacotePorTipo.get(tipo);
                if (pacoteDoTipo != null
                        && !pacoteDoTipo.equals(a.pacote())
                        && pacotesDeDominio.contains(pacoteDoTipo)) {
                    culpados.computeIfAbsent(a.nome(), k -> new TreeSet<>())
                            .add("@" + m.group(1) + " " + pacoteDoTipo + "." + tipo);
                }
            }
        }

        assertThat(culpados)
                .as("""
                        Associacao JPA para entidade de outro pacote de dominio. Entre \
                        agregados a referencia e o ID (coluna FK em @Column, como \
                        Candidatura.vagaId, Vaga.recrutadorId e Curriculo.candidatoId), e quem \
                        precisa dos dados do outro lado pergunta a porta do modulo dono. \
                        @ManyToOne atravessando agregado e o que obrigava o \
                        Hibernate.initialize e criava os ciclos. Dentro do MESMO pacote a \
                        associacao e legitima: CriterioAceite aponta para Vaga porque e parte \
                        do agregado Vaga. Violacao(oes): %s""", culpados)
                .isEmpty();
    }

    @Test
    @DisplayName("notificacoes nao conhece candidaturas: o aviso chega pronto em AvisoAoCandidato, medida conquistada por esta refatoracao")
    void notificacoesNaoImportaCandidaturas() {
        assertThat(importadoresDe("notificacoes", "candidaturas"))
                .as("""
                        notificacoes voltou a importar candidaturas. Quem monta o \
                        AvisoAoCandidato e a triagem, que ja tem os dados em mao; \
                        notificacoes recebe o record pronto e nao sabe o que e uma \
                        candidatura. Eram 5 imports antes da refatoracao. Arquivo(s): %s""",
                        importadoresDe("notificacoes", "candidaturas"))
                .isEmpty();
    }

    @Test
    @DisplayName("candidaturas nao conhece triagem: o enfileiramento e porta de saida declarada em candidaturas e implementada em triagem")
    void candidaturasNaoImportaTriagem() {
        assertThat(importadoresDe("candidaturas", "triagem"))
                .as("""
                        candidaturas voltou a importar triagem. A dependencia e invertida de \
                        proposito: candidaturas DECLARA EnfileiramentoDeTriagem e triagem a \
                        implementa, e e isso que mantem o grafo sem ciclo. Eram 2 imports \
                        antes da refatoracao. Arquivo(s): %s""",
                        importadoresDe("candidaturas", "triagem"))
                .isEmpty();
    }

    /** Arquivos do pacote {@code de} que importam algo do pacote {@code para}. */
    @Test
    @DisplayName("resposta completa de vaga so em chamador declarado: a expressao de busca nao vaza por descuido de camada")
    void respostaCompletaSoEmChamadorDeclarado() {
        // Esta regra existe porque a invariante estava sendo mantida pelo
        // template, e nao pelo codigo: as paginas de vagas montavam a resposta
        // completa, e nenhuma expressao saia no HTML apenas porque nenhum
        // template a imprimia. Quem amanha acrescentasse a expressao em
        // vagas.html vazaria sem tocar em nada chamado "seguranca".
        Map<String, String> chamadores = new TreeMap<>();
        for (ArquivoJava a : arquivos) {
            if (MONTAGEM_COMPLETA.matcher(a.texto()).find()) {
                chamadores.put(a.nome(), a.pacote());
            }
        }

        Set<String> naoDeclarados = new TreeSet<>(chamadores.keySet());
        naoDeclarados.removeAll(PODE_MONTAR_RESPOSTA_COMPLETA.keySet());
        assertThat(naoDeclarados)
                .as("Chamador nao declarado de completa(...)/completas(...). Essa resposta carrega"
                        + " a expressao de busca dos criterios, que o candidato nao pode ver. Use"
                        + " paraQuemPergunta(vaga, usuario), que apaga a expressao de quem nao e o"
                        + " RH dono. Se quem chama ja e o dono por construcao, declare o motivo em"
                        + " PODE_MONTAR_RESPOSTA_COMPLETA. Chamador(es) novo(s): %s", naoDeclarados)
                .isEmpty();

        Set<String> declaracaoObsoleta = new TreeSet<>(PODE_MONTAR_RESPOSTA_COMPLETA.keySet());
        declaracaoObsoleta.removeAll(chamadores.keySet());
        assertThat(declaracaoObsoleta)
                .as("entrada(s) obsoleta(s) em PODE_MONTAR_RESPOSTA_COMPLETA: "
                        + "o arquivo nao monta mais a resposta completa; apague a linha")
                .isEmpty();
    }

    private static List<String> importadoresDe(String de, String para) {
        return arquivos.stream()
                .filter(a -> a.pacote().equals(de))
                .filter(a -> IMPORT_INTERNO.matcher(a.texto()).results()
                        .anyMatch(r -> r.group(1).equals(para)))
                .map(ArquivoJava::nome)
                .toList();
    }

    private static Map<String, Set<String>> grafoDeDominio() {
        Map<String, Set<String>> grafo = new LinkedHashMap<>();
        pacotesDeDominio.forEach(p -> grafo.put(p, new TreeSet<>()));
        for (ArquivoJava a : arquivos) {
            if (!pacotesDeDominio.contains(a.pacote())) {
                continue;
            }
            IMPORT_INTERNO.matcher(a.texto()).results()
                    .map(r -> r.group(1))
                    .filter(pacotesDeDominio::contains)
                    .filter(destino -> !destino.equals(a.pacote()))
                    .forEach(destino -> grafo.get(a.pacote()).add(destino));
        }
        return grafo;
    }

    /**
     * @return o ciclo encontrado, comecando e terminando no mesmo pacote, ou
     *     {@code null} se o grafo for aciclico
     */
    private static List<String> procurarCiclo(Map<String, Set<String>> grafo) {
        Set<String> encerrados = new LinkedHashSet<>();
        for (String inicio : grafo.keySet().stream().sorted(Comparator.naturalOrder()).toList()) {
            List<String> caminho = new ArrayList<>();
            List<String> ciclo = descer(inicio, grafo, caminho, encerrados);
            if (ciclo != null) {
                return ciclo;
            }
        }
        return null;
    }

    private static List<String> descer(String pacote,
                                       Map<String, Set<String>> grafo,
                                       List<String> caminho,
                                       Set<String> encerrados) {
        if (caminho.contains(pacote)) {
            List<String> ciclo = new ArrayList<>(caminho.subList(caminho.indexOf(pacote), caminho.size()));
            ciclo.add(pacote);
            return ciclo;
        }
        if (encerrados.contains(pacote)) {
            return null;
        }
        caminho.add(pacote);
        for (String destino : grafo.getOrDefault(pacote, Set.of())) {
            List<String> ciclo = descer(destino, grafo, caminho, encerrados);
            if (ciclo != null) {
                return ciclo;
            }
        }
        caminho.remove(caminho.size() - 1);
        encerrados.add(pacote);
        return null;
    }

    /**
     * Tipo do campo anotado que comeca em {@code posicao}: le da anotacao ate o
     * primeiro {@code ;}, que num campo anotado e o fim da declaracao.
     */
    private static String tipoDoCampoApos(String texto, int posicao) {
        int fim = texto.indexOf(';', posicao);
        if (fim < 0) {
            return null;
        }
        Matcher m = TIPO_DO_CAMPO.matcher(texto.substring(posicao, fim + 1));
        return m.find() ? m.group(1) : null;
    }
}
