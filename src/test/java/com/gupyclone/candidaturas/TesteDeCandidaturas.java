package com.gupyclone.candidaturas;

import com.gupyclone.curriculos.CurriculoRepository;
import com.gupyclone.pessoas.Candidato;
import com.gupyclone.pessoas.CandidatoRepository;
import com.gupyclone.pessoas.Recrutador;
import com.gupyclone.pessoas.RecrutadorRepository;
import com.gupyclone.suporte.FabricaDePdf;
import com.gupyclone.suporte.TesteDeIntegracao;
import com.gupyclone.triagem.AnaliseJobRepository;
import com.gupyclone.vagas.ServicoDeVagas;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Base dos testes de candidatura: as personas e uma vaga aberta de verdade.
 *
 * <p>As pessoas extras ({@code bia} e os candidatos de concorrencia) nascem com
 * {@code findByChave(...).orElseGet(...)} porque o limpador de banco preserva
 * candidato e recrutador entre testes: recriar violaria o unique de chave e de
 * e-mail na segunda classe de teste da mesma JVM.
 */
abstract class TesteDeCandidaturas extends TesteDeIntegracao {

    protected static final String TITULO_DA_VAGA = "Pessoa Desenvolvedora Java";

    /** Curriculo com camada de texto: lido sem OCR, portanto rapido. */
    protected static final byte[] PDF_COM_TEXTO = FabricaDePdf.comCamadaDeTexto(
            "Carlos Pereira",
            "Desenvolvedor Java com Spring Boot",
            "Experiencia com PostgreSQL e Docker");

    @Autowired
    protected ServicoDeCandidaturas servico;

    @Autowired
    protected ServicoDeVagas servicoDeVagas;

    @Autowired
    protected CandidaturaRepository candidaturas;

    @Autowired
    protected AnaliseJobRepository analises;

    @Autowired
    protected CurriculoRepository curriculos;

    @Autowired
    protected CandidatoRepository candidatos;

    @Autowired
    protected RecrutadorRepository recrutadores;

    protected Long ana;
    protected Long bia;
    protected Long carlos;
    protected Long joao;
    protected Long vaga;

    @BeforeEach
    void prepararPersonasEVagaAberta() {
        ana = recrutadores.findByChave("ana").orElseThrow().getId();
        bia = recrutadores.findByChave("bia")
                .orElseGet(() -> recrutadores.save(
                        new Recrutador("bia", "Bia Martins", "bia.martins@gupyclone.com.br")))
                .getId();
        carlos = candidatos.findByChave("carlos").orElseThrow().getId();
        joao = candidatos.findByChave("joao").orElseThrow().getId();
        vaga = publicarVaga(TITULO_DA_VAGA);
    }

    protected Long publicarVaga(String titulo) {
        return servicoDeVagas.publicar(ana, titulo, "Descrição de " + titulo, 60,
                        List.of(new ServicoDeVagas.NovoCriterio("Java", "java", 50, true),
                                new ServicoDeVagas.NovoCriterio("PostgreSQL", "postgresql or postgres", 30, false)))
                .getId();
    }

    /** Candidato extra, reaproveitado quando ja existe de um teste anterior. */
    protected Long candidatoExtra(String chave, String nome) {
        return candidatos.findByChave(chave)
                .orElseGet(() -> candidatos.save(new Candidato(chave, nome, chave + "@exemplo.com.br")))
                .getId();
    }
}
