package com.gupyclone.triagem;

import com.gupyclone.candidaturas.Candidatura;
import com.gupyclone.candidaturas.CandidaturaRepository;
import com.gupyclone.candidaturas.ServicoDeCandidaturas;
import com.gupyclone.curriculos.Curriculo;
import com.gupyclone.curriculos.CurriculoRepository;
import com.gupyclone.notificacoes.EmailOutboxRepository;
import com.gupyclone.pessoas.Candidato;
import com.gupyclone.pessoas.CandidatoRepository;
import com.gupyclone.pessoas.RecrutadorRepository;
import com.gupyclone.suporte.FabricaDePdf;
import com.gupyclone.suporte.TesteDeIntegracao;
import com.gupyclone.vagas.ServicoDeVagas;
import com.gupyclone.vagas.Vaga;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Base dos testes de triagem: personas de verdade, vagas publicadas pelo
 * servico de vagas e candidaturas feitas pelo servico de candidaturas, com PDFs
 * reais.
 *
 * <p>Nenhum contexto de analise e inventado: {@link #contextoDe} monta o
 * contexto a partir de uma candidatura que existe no banco, exatamente como o
 * processador o monta em producao. Assim um teste de analisador nao pode passar
 * com dados que o resto do sistema nunca produziria.
 *
 * <p>Pessoas extras nascem com {@code findByChave(...).orElseGet(...)} porque o
 * limpador de banco preserva candidato e recrutador entre testes: recriar
 * violaria o unique de chave e de e-mail.
 */
abstract class TesteDeTriagem extends TesteDeIntegracao {

    protected static final String TITULO_DA_VAGA = "Pessoa Desenvolvedora Java";

    /** Curriculo que atende os criterios da vaga de exemplo, com acentuacao. */
    protected static final byte[] PDF_QUE_ATENDE = FabricaDePdf.comCamadaDeTexto(
            "Carlos Pereira",
            "Desenvolvedor Java com Spring Boot e PostgreSQL",
            "Pós-Graduação em Engenharia de Software",
            "Inglês fluente e espanhol básico");

    /** Curriculo de outra carreira: nenhum criterio da vaga de exemplo e atendido. */
    protected static final byte[] PDF_QUE_NAO_ATENDE = FabricaDePdf.comCamadaDeTexto(
            "Carlos Pereira",
            "Analista de sistemas com foco em COBOL e DB2",
            "Experiencia com mainframe, JCL e CICS");

    @Autowired
    protected ServicoDeVagas servicoDeVagas;

    @Autowired
    protected ServicoDeCandidaturas servicoDeCandidaturas;

    @Autowired
    protected CandidaturaRepository candidaturas;

    @Autowired
    protected AnaliseJobRepository analises;

    @Autowired
    protected EmailOutboxRepository emails;

    @Autowired
    protected CurriculoRepository curriculos;

    @Autowired
    protected CandidatoRepository candidatos;

    @Autowired
    protected RecrutadorRepository recrutadores;

    @Autowired
    protected JdbcClient jdbc;

    protected Long ana;
    protected Long carlos;
    protected Long joao;

    @BeforeEach
    void localizarPersonas() {
        ana = recrutadores.findByChave("ana").orElseThrow().getId();
        carlos = candidatos.findByChave("carlos").orElseThrow().getId();
        joao = candidatos.findByChave("joao").orElseThrow().getId();
    }

    protected Vaga publicarVaga(String titulo, int pontuacaoMinima, ServicoDeVagas.NovoCriterio... criterios) {
        return servicoDeVagas.publicar(ana, titulo, "Descrição de " + titulo, pontuacaoMinima, List.of(criterios));
    }

    /** A vaga de exemplo: Java obrigatorio, PostgreSQL e inglês opcionais. */
    protected Vaga publicarVagaDeExemplo(int pontuacaoMinima) {
        return publicarVaga(TITULO_DA_VAGA, pontuacaoMinima,
                new ServicoDeVagas.NovoCriterio("Java", "java", 50, true),
                new ServicoDeVagas.NovoCriterio("PostgreSQL", "postgresql or postgres", 30, false),
                new ServicoDeVagas.NovoCriterio("Inglês", "ingles", 20, false));
    }

    protected Candidatura candidatar(Long candidatoId, Long vagaId, byte[] pdf) {
        return servicoDeCandidaturas.candidatar(candidatoId, vagaId, "curriculo-" + candidatoId + ".pdf", pdf);
    }

    /**
     * Monta o contexto de uma candidatura que existe, com os criterios da vaga
     * e o texto que a extracao de PDF gravou.
     */
    protected ContextoDaAnalise contextoDe(Candidatura candidatura) {
        Vaga vaga = servicoDeVagas.porId(candidatura.getVaga().getId());
        Curriculo curriculo = curriculos.findById(candidatura.getCurriculo().getId()).orElseThrow();
        List<ContextoDaAnalise.Criterio> criterios = vaga.getCriterios().stream()
                .map(criterio -> new ContextoDaAnalise.Criterio(criterio.getId(),
                        criterio.getDescricao(),
                        criterio.getExpressao(),
                        criterio.getPeso(),
                        criterio.isObrigatorio()))
                .toList();
        return new ContextoDaAnalise(candidatura.getId(),
                vaga.getId(),
                vaga.getTitulo(),
                vaga.getDescricao(),
                vaga.getPontuacaoMinima(),
                curriculo.getId(),
                curriculo.getTexto(),
                criterios);
    }

    /** Candidato extra, reaproveitado quando ja existe de um teste anterior. */
    protected Long candidatoExtra(String chave, String nome) {
        return candidatos.findByChave(chave)
                .orElseGet(() -> candidatos.save(new Candidato(chave, nome, chave + "@exemplo.com.br")))
                .getId();
    }
}
