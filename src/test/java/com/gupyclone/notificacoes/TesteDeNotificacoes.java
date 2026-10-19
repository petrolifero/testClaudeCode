package com.gupyclone.notificacoes;

import com.gupyclone.candidaturas.Candidatura;
import com.gupyclone.candidaturas.CandidaturaRepository;
import com.gupyclone.curriculos.Curriculo;
import com.gupyclone.curriculos.CurriculoRepository;
import com.gupyclone.curriculos.OrigemTexto;
import com.gupyclone.curriculos.TextoExtraido;
import com.gupyclone.pessoas.Candidato;
import com.gupyclone.pessoas.CandidatoRepository;
import com.gupyclone.pessoas.Recrutador;
import com.gupyclone.pessoas.RecrutadorRepository;
import com.gupyclone.suporte.FabricaDePdf;
import com.gupyclone.suporte.TesteDeIntegracao;
import com.gupyclone.vagas.Vaga;
import com.gupyclone.vagas.VagaRepository;
import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Base dos testes de notificacao.
 *
 * <p>O SMTP e um GreenMail de verdade na porta 3025, a mesma que o
 * {@code application-test.yml} entrega ao {@code JavaMailSender}: o caminho
 * exercitado e o mesmo de producao, da montagem da MimeMessage ate o dialogo
 * SMTP. A autenticacao e desligada porque o {@code UserManager} do GreenMail
 * ja nasce exigindo login, e a aplicacao nao manda credencial nenhuma.
 */
abstract class TesteDeNotificacoes extends TesteDeIntegracao {

    @RegisterExtension
    static final GreenMailExtension smtp = new GreenMailExtension(ServerSetupTest.SMTP)
            .withConfiguration(GreenMailConfiguration.aConfig().withDisabledAuthentication());

    /** Conteudo ASCII de proposito: o que se afirma aqui e o e-mail, nao o PDF. */
    private static final byte[] PDF = FabricaDePdf.comCamadaDeTexto(
            "Carreira em Java, Spring Boot e PostgreSQL",
            "Ingles intermediario");

    @Autowired
    protected EmailOutboxRepository emails;

    @Autowired
    protected FilaDeEmails fila;

    @Autowired
    protected CandidaturaRepository candidaturas;

    @Autowired
    protected TransactionTemplate transacao;

    @Autowired
    private CandidatoRepository candidatos;

    @Autowired
    private RecrutadorRepository recrutadores;

    @Autowired
    private VagaRepository vagas;

    @Autowired
    private CurriculoRepository curriculos;

    /**
     * Grava vaga, curriculo e candidatura de verdade pelos repositorios.
     *
     * @return o id da candidatura
     */
    protected Long gravarCandidatura(String chaveDoCandidato, String tituloDaVaga) {
        Candidato candidato = candidatos.findByChave(chaveDoCandidato).orElseThrow();
        Recrutador ana = recrutadores.findByChave("ana").orElseThrow();
        Vaga vaga = vagas.save(new Vaga(tituloDaVaga, "Descrição de " + tituloDaVaga, ana.getId(), 60));
        TextoExtraido extraido = new TextoExtraido(
                "Carreira em Java, Spring Boot e PostgreSQL. Ingles intermediario.",
                OrigemTexto.CAMADA_DE_TEXTO,
                1);
        Curriculo curriculo = curriculos.save(
                new Curriculo(candidato, "curriculo-" + chaveDoCandidato + ".pdf", PDF, extraido));
        return candidaturas.save(new Candidatura(
                vaga.getId(), candidato.getId(), curriculo.getId(), ana.getId())).getId();
    }

    /**
     * Enfileira como a triagem vai enfileirar: dentro de uma transacao e com o
     * aviso ja montado, que e tudo o que a fila conhece.
     */
    protected void enfileirar(Long candidaturaId, TipoEmail tipo, int pontuacao, String motivo) {
        AvisoAoCandidato aviso = avisoDe(candidaturaId, tipo, pontuacao, motivo);
        transacao.executeWithoutResult(status -> fila.enfileirar(aviso));
    }

    /**
     * O mesmo aviso que a triagem monta: destinatario, nome e titulo da vaga
     * buscados pelos ids que a candidatura guarda.
     */
    protected AvisoAoCandidato avisoDe(Long candidaturaId, TipoEmail tipo, int pontuacao, String motivo) {
        Candidatura candidatura = candidaturas.findById(candidaturaId).orElseThrow();
        Candidato candidato = candidatos.findById(candidatura.getCandidatoId()).orElseThrow();
        String tituloDaVaga = vagas.findById(candidatura.getVagaId()).orElseThrow().getTitulo();
        return new AvisoAoCandidato(candidaturaId, tipo, candidato.getEmail(), candidato.getNome(),
                tituloDaVaga, pontuacao, motivo);
    }

    protected static String assuntoDe(MimeMessage mensagem) throws MessagingException {
        return mensagem.getSubject();
    }

    /** O {@code getContent} desfaz o quoted-printable e aplica o charset declarado. */
    protected static String corpoDe(MimeMessage mensagem) throws MessagingException, IOException {
        return (String) mensagem.getContent();
    }

    protected static String paraDe(MimeMessage mensagem) throws MessagingException {
        return mensagem.getRecipients(Message.RecipientType.TO)[0].toString();
    }

    protected static String remetenteDe(MimeMessage mensagem) throws MessagingException {
        return mensagem.getFrom()[0].toString();
    }
}
