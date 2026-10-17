package com.gupyclone.vagas;

import com.gupyclone.pessoas.Recrutador;
import com.gupyclone.pessoas.RecrutadorRepository;
import java.util.List;
import org.hibernate.Hibernate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Vagas do ponto de vista do RH: publicar com criterios de aceite, listar e
 * fechar.
 *
 * <p>O dono da vaga e sempre o recrutador autenticado. Nenhum metodo aqui
 * aceita o papel como parametro: quem decide se a acao e permitida e a
 * comparacao com o recrutador que publicou a vaga.
 */
@Service
@Transactional
public class ServicoDeVagas {

    /** O mesmo limite da coluna {@code vaga.titulo}. */
    private static final int MAXIMO_DO_TITULO = 160;

    /** O mesmo limite das colunas {@code criterio_aceite.descricao} e {@code .expressao}. */
    private static final int MAXIMO_DO_CRITERIO = 200;

    /**
     * Criterio ainda sem vaga, como o RH acabou de descrever.
     *
     * @param descricao   o que o criterio cobra, em linguagem de gente
     * @param expressao   o que a triagem procura no curriculo
     * @param peso        quanto o criterio vale na pontuacao, de 1 a 100
     * @param obrigatorio se nao atende-lo reprova a candidatura na hora
     */
    public record NovoCriterio(String descricao, String expressao, int peso, boolean obrigatorio) {
    }

    private final VagaRepository vagas;
    private final RecrutadorRepository recrutadores;

    public ServicoDeVagas(VagaRepository vagas, RecrutadorRepository recrutadores) {
        this.vagas = vagas;
        this.recrutadores = recrutadores;
    }

    public Vaga publicar(Long recrutadorId,
                         String titulo,
                         String descricao,
                         int pontuacaoMinima,
                         List<NovoCriterio> criterios) {
        if (recrutadorId == null) {
            throw new IllegalArgumentException("a vaga precisa de um recrutador");
        }
        Recrutador recrutador = recrutadores.findById(recrutadorId)
                .orElseThrow(() -> new IllegalArgumentException("recrutador inexistente: " + recrutadorId));

        Vaga vaga = new Vaga(texto(titulo, MAXIMO_DO_TITULO, "o título da vaga"),
                exigirPreenchido(descricao, "a descrição da vaga"),
                recrutador,
                pontuacaoMinima);
        if (criterios != null) {
            for (NovoCriterio novo : criterios) {
                vaga.adicionarCriterio(new CriterioAceite(
                        texto(novo.descricao(), MAXIMO_DO_CRITERIO, "a descrição do critério"),
                        texto(novo.expressao(), MAXIMO_DO_CRITERIO, "a expressão do critério"),
                        novo.peso(),
                        novo.obrigatorio()));
            }
        }
        return carregada(vagas.save(vaga));
    }

    @Transactional(readOnly = true)
    public List<Vaga> abertas() {
        return carregadas(vagas.findAllByStatusOrderByCriadoEmDesc(StatusVaga.ABERTA));
    }

    @Transactional(readOnly = true)
    public Vaga porId(Long id) {
        return carregada(vagas.buscarComCriterios(id).orElseThrow(() -> new VagaInexistenteException(id)));
    }

    @Transactional(readOnly = true)
    public List<Vaga> doRecrutador(Long recrutadorId) {
        return carregadas(vagas.findAllByRecrutadorIdOrderByCriadoEmDesc(recrutadorId));
    }

    public void fechar(Long vagaId, Long recrutadorId) {
        Vaga vaga = vagas.findById(vagaId).orElseThrow(() -> new VagaInexistenteException(vagaId));
        if (!vaga.getRecrutador().getId().equals(recrutadorId)) {
            throw new AccessDeniedException("somente quem publicou a vaga pode fechá-la");
        }
        vaga.fechar();
    }

    /**
     * Carrega recrutador e criterios antes de a transacao fechar.
     *
     * <p>Com {@code open-in-view: false} a vaga chega destacada a quem chamou,
     * e qualquer associacao preguicosa que sobrasse viraria
     * {@code LazyInitializationException} na montagem da resposta.
     */
    private static Vaga carregada(Vaga vaga) {
        Hibernate.initialize(vaga.getRecrutador());
        // getCriterios() devolve uma lista imutavel em volta da colecao
        // gerenciada, e Hibernate.initialize nao reconheceria esse envelope;
        // tocar no tamanho inicializa a colecao de verdade.
        vaga.getCriterios().size();
        return vaga;
    }

    private static List<Vaga> carregadas(List<Vaga> encontradas) {
        encontradas.forEach(ServicoDeVagas::carregada);
        return encontradas;
    }

    private static String texto(String valor, int maximo, String oQue) {
        String limpo = exigirPreenchido(valor, oQue);
        if (limpo.length() > maximo) {
            throw new IllegalArgumentException(oQue + " passa de " + maximo + " caracteres");
        }
        return limpo;
    }

    private static String exigirPreenchido(String valor, String oQue) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(oQue + " não pode ficar em branco");
        }
        return valor.strip();
    }
}
