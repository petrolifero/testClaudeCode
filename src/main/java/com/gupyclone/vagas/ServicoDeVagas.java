package com.gupyclone.vagas;

import com.gupyclone.pessoas.ConsultaDePessoas;
import java.util.List;
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
 *
 * <p>Um unico repositorio, o do proprio agregado. O recrutador e outro
 * agregado: entra como id e so e consultado pela porta de pessoas, para recusar
 * publicacao em nome de quem nao existe. As vagas devolvidas ja vem com os
 * criterios no mesmo SELECT (ver {@link VagaRepository#COM_CRITERIOS}), por
 * isso nenhuma delas tem associacao preguicosa sobrando para quem monta a
 * resposta fora da transacao.
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
    private final ValidadorDeExpressaoDeBusca validador;
    private final ConsultaDePessoas pessoas;

    public ServicoDeVagas(VagaRepository vagas,
                          ConsultaDePessoas pessoas,
                          ValidadorDeExpressaoDeBusca validador) {
        this.vagas = vagas;
        this.pessoas = pessoas;
        this.validador = validador;
    }

    public Vaga publicar(Long recrutadorId,
                         String titulo,
                         String descricao,
                         int pontuacaoMinima,
                         List<NovoCriterio> criterios) {
        if (recrutadorId == null) {
            throw new IllegalArgumentException("a vaga precisa de um recrutador");
        }
        // Checagem pela porta, e nao pela chave estrangeira: a violacao de FK
        // chegaria como DataIntegrityViolationException no flush, sem dizer qual
        // dos ids da vaga estava errado.
        if (pessoas.recrutador(recrutadorId).isEmpty()) {
            throw new IllegalArgumentException("recrutador inexistente: " + recrutadorId);
        }

        Vaga vaga = new Vaga(texto(titulo, MAXIMO_DO_TITULO, "o título da vaga"),
                exigirPreenchido(descricao, "a descrição da vaga"),
                recrutadorId,
                pontuacaoMinima);
        if (criterios != null) {
            for (NovoCriterio novo : criterios) {
                String descricaoDoCriterio = texto(novo.descricao(), MAXIMO_DO_CRITERIO,
                        "a descrição do critério");
                String expressao = texto(novo.expressao(), MAXIMO_DO_CRITERIO,
                        "a expressão do critério");
                // Recusa aqui expressao que nenhum curriculo poderia atender:
                // e o unico momento em que o RH pode corrigir.
                validador.exigirUtil(descricaoDoCriterio, expressao);
                vaga.adicionarCriterio(new CriterioAceite(
                        descricaoDoCriterio,
                        expressao,
                        novo.peso(),
                        novo.obrigatorio()));
            }
        }
        return vagas.save(vaga);
    }

    @Transactional(readOnly = true)
    public List<Vaga> abertas() {
        return vagas.buscarPorStatusComCriterios(StatusVaga.ABERTA);
    }

    @Transactional(readOnly = true)
    public Vaga porId(Long id) {
        return vagas.buscarComCriterios(id).orElseThrow(() -> new VagaInexistenteException(id));
    }

    @Transactional(readOnly = true)
    public List<Vaga> doRecrutador(Long recrutadorId) {
        return vagas.buscarDoRecrutadorComCriterios(recrutadorId);
    }

    public void fechar(Long vagaId, Long recrutadorId) {
        Vaga vaga = vagas.findById(vagaId).orElseThrow(() -> new VagaInexistenteException(vagaId));
        if (!vaga.ehDoRecrutador(recrutadorId)) {
            throw new AccessDeniedException("somente quem publicou a vaga pode fechá-la");
        }
        vaga.fechar();
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
