package com.gupyclone.web;

import com.gupyclone.candidaturas.CandidaturaResposta;
import com.gupyclone.candidaturas.ServicoDeCandidaturas;
import com.gupyclone.seguranca.UsuarioAutenticado;
import com.gupyclone.vagas.MontadorDeVagaResposta;
import com.gupyclone.vagas.ServicoDeVagas;
import com.gupyclone.vagas.ServicoDeVagas.NovoCriterio;
import com.gupyclone.vagas.VagaResposta;
import java.util.ArrayList;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** As paginas de quem publica vaga. */
@Controller
@PreAuthorize("hasRole('RH')")
public class PaginaDoRhController {

    /** Quantidade de linhas de criterio no formulario. Linhas vazias sao ignoradas. */
    static final int LINHAS_DE_CRITERIO = 5;

    private final ServicoDeVagas vagas;
    private final MontadorDeVagaResposta montador;
    private final ServicoDeCandidaturas candidaturas;

    public PaginaDoRhController(ServicoDeVagas vagas,
                                MontadorDeVagaResposta montador,
                                ServicoDeCandidaturas candidaturas) {
        this.vagas = vagas;
        this.montador = montador;
        this.candidaturas = candidaturas;
    }

    /** Uma vaga do RH com as candidaturas que ela recebeu. */
    public record VagaComCandidaturas(VagaResposta vaga, List<CandidaturaResposta> candidaturas) {
    }

    @GetMapping("/rh/vagas")
    public String minhasVagas(@AuthenticationPrincipal UsuarioAutenticado usuario, Model modelo) {
        List<VagaComCandidaturas> lista = montador.completas(vagas.doRecrutador(usuario.entidadeId())).stream()
                .map(vaga -> new VagaComCandidaturas(
                        vaga,
                        candidaturas.daVaga(vaga.id(), usuario.entidadeId())))
                .toList();
        modelo.addAttribute("vagas", lista);
        return "rh/vagas";
    }

    @GetMapping("/rh/vagas/nova")
    public String formulario(Model modelo) {
        modelo.addAttribute("linhas", LINHAS_DE_CRITERIO);
        return "rh/nova-vaga";
    }

    /**
     * Recebe as linhas de criterio como listas paralelas.
     *
     * <p>A obrigatoriedade vem de um {@code select}, e nao de um checkbox: o
     * navegador nao envia checkbox desmarcado, o que desalinharia as listas
     * paralelas e faria o criterio 3 herdar a obrigatoriedade do criterio 5.
     */
    @PostMapping("/rh/vagas")
    public String publicar(@AuthenticationPrincipal UsuarioAutenticado usuario,
                           @RequestParam String titulo,
                           @RequestParam String descricao,
                           @RequestParam(defaultValue = "0") int pontuacaoMinima,
                           @RequestParam(name = "criterioDescricao", required = false) List<String> descricoes,
                           @RequestParam(name = "criterioExpressao", required = false) List<String> expressoes,
                           @RequestParam(name = "criterioPeso", required = false) List<Integer> pesos,
                           @RequestParam(name = "criterioObrigatorio", required = false) List<Boolean> obrigatorios,
                           RedirectAttributes atributos) {
        try {
            var vaga = vagas.publicar(usuario.entidadeId(), titulo, descricao, pontuacaoMinima,
                    criterios(descricoes, expressoes, pesos, obrigatorios));
            atributos.addFlashAttribute("sucesso", "Vaga \"" + vaga.getTitulo() + "\" publicada.");
            return "redirect:/rh/vagas";
        } catch (IllegalArgumentException e) {
            atributos.addFlashAttribute("erro", e.getMessage());
            return "redirect:/rh/vagas/nova";
        }
    }

    private static List<NovoCriterio> criterios(List<String> descricoes,
                                                List<String> expressoes,
                                                List<Integer> pesos,
                                                List<Boolean> obrigatorios) {
        if (descricoes == null) {
            return List.of();
        }
        List<NovoCriterio> criterios = new ArrayList<>();
        for (int linha = 0; linha < descricoes.size(); linha++) {
            String descricao = texto(descricoes, linha);
            String expressao = texto(expressoes, linha);
            if (descricao.isBlank() || expressao.isBlank()) {
                continue;
            }
            criterios.add(new NovoCriterio(descricao, expressao,
                    pesos != null && linha < pesos.size() && pesos.get(linha) != null ? pesos.get(linha) : 1,
                    obrigatorios != null && linha < obrigatorios.size() && Boolean.TRUE.equals(obrigatorios.get(linha))));
        }
        return criterios;
    }

    private static String texto(List<String> valores, int linha) {
        return valores == null || linha >= valores.size() || valores.get(linha) == null ? "" : valores.get(linha).strip();
    }
}
