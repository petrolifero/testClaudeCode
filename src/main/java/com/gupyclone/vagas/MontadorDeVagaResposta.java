package com.gupyclone.vagas;

import com.gupyclone.pessoas.ConsultaDePessoas;
import com.gupyclone.seguranca.UsuarioAutenticado;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Monta {@link VagaResposta} a partir de uma {@link Vaga}, resolvendo pela
 * porta de pessoas o nome de quem publicou.
 *
 * <p>Numa lista o nome e resolvido uma vez por recrutador distinto, e nao uma
 * vez por vaga: sem isso o N+1 que o fetch dos criterios eliminou voltaria pela
 * outra ponta.
 */
@Component
public class MontadorDeVagaResposta {

    private final ConsultaDePessoas pessoas;

    public MontadorDeVagaResposta(ConsultaDePessoas pessoas) {
        this.pessoas = pessoas;
    }

    /** Resposta com as expressoes de busca; so para quem pode ve-las. */
    public VagaResposta completa(Vaga vaga) {
        return VagaResposta.de(vaga, nomeDoRecrutador(vaga.getRecrutadorId()));
    }

    public List<VagaResposta> completas(List<Vaga> vagas) {
        Map<Long, String> nomes = new HashMap<>();
        return vagas.stream().map(vaga -> VagaResposta.de(vaga, nome(nomes, vaga))).toList();
    }

    /** So o RH dono da vaga ve as expressoes de busca dos criterios. */
    public VagaResposta paraQuemPergunta(Vaga vaga, UsuarioAutenticado usuario) {
        return montar(vaga, nomeDoRecrutador(vaga.getRecrutadorId()), usuario);
    }

    public List<VagaResposta> paraQuemPergunta(List<Vaga> vagas, UsuarioAutenticado usuario) {
        Map<Long, String> nomes = new HashMap<>();
        return vagas.stream().map(vaga -> montar(vaga, nome(nomes, vaga), usuario)).toList();
    }

    private static VagaResposta montar(Vaga vaga, String nomeDoRecrutador, UsuarioAutenticado usuario) {
        boolean ehODono = usuario != null && usuario.ehRh() && vaga.ehDoRecrutador(usuario.entidadeId());
        return ehODono
                ? VagaResposta.de(vaga, nomeDoRecrutador)
                : VagaResposta.semExpressoes(vaga, nomeDoRecrutador);
    }

    private String nome(Map<Long, String> nomes, Vaga vaga) {
        return nomes.computeIfAbsent(vaga.getRecrutadorId(), this::nomeDoRecrutador);
    }

    /**
     * A chave estrangeira garante que o recrutador existe, portanto a ausencia
     * aqui e defeito de programacao, nao caminho de usuario.
     */
    private String nomeDoRecrutador(Long recrutadorId) {
        return pessoas.recrutador(recrutadorId)
                .orElseThrow(() -> new IllegalStateException("recrutador inexistente: " + recrutadorId))
                .nome();
    }
}
