package com.gupyclone.web;

import com.gupyclone.candidaturas.CandidaturaController.CandidaturaResposta;
import com.gupyclone.candidaturas.CandidaturaDuplicadaException;
import com.gupyclone.candidaturas.ServicoDeCandidaturas;
import com.gupyclone.candidaturas.VagaFechadaException;
import com.gupyclone.comum.ErroDeNegocio;
import com.gupyclone.curriculos.CurriculoInvalidoException;
import com.gupyclone.seguranca.UsuarioAutenticado;
import com.gupyclone.vagas.ServicoDeVagas;
import com.gupyclone.vagas.VagaController.VagaResposta;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Optional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** As paginas de quem procura vaga. */
@Controller
public class PaginaDeVagasController {

    private final ServicoDeVagas vagas;
    private final ServicoDeCandidaturas candidaturas;

    public PaginaDeVagasController(ServicoDeVagas vagas, ServicoDeCandidaturas candidaturas) {
        this.vagas = vagas;
        this.candidaturas = candidaturas;
    }

    @GetMapping("/vagas")
    public String listar(Model modelo) {
        modelo.addAttribute("vagas", vagas.abertas().stream().map(VagaResposta::de).toList());
        return "vagas";
    }

    @GetMapping("/vagas/{id}")
    public String detalhar(@PathVariable Long id,
                           @AuthenticationPrincipal UsuarioAutenticado usuario,
                           Model modelo) {
        VagaResposta vaga = VagaResposta.de(vagas.porId(id));
        modelo.addAttribute("vaga", vaga);
        // Candidato que ja se inscreveu ve o andamento no lugar do formulario.
        modelo.addAttribute("minhaCandidatura", usuario.ehCandidato()
                ? candidaturaDe(usuario, id).orElse(null)
                : null);
        return "vaga";
    }

    @PostMapping("/vagas/{id}/candidatar")
    @PreAuthorize("hasRole('CANDIDATO')")
    public String candidatar(@PathVariable Long id,
                             @AuthenticationPrincipal UsuarioAutenticado usuario,
                             @RequestPart(name = "arquivo", required = false) MultipartFile arquivo,
                             RedirectAttributes atributos) {
        try {
            candidaturas.candidatar(usuario.entidadeId(), id, nomeDoArquivo(arquivo), conteudo(arquivo));
            atributos.addFlashAttribute("sucesso",
                    "Currículo enviado. A análise acontece em instantes; acompanhe em Minhas candidaturas.");
        } catch (CandidaturaDuplicadaException | VagaFechadaException | CurriculoInvalidoException e) {
            // Erro esperado do fluxo: volta para a vaga com o recado, em vez de
            // uma pagina de erro que nao diz o que fazer em seguida.
            atributos.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/vagas/" + id;
    }

    @GetMapping("/minhas-candidaturas")
    @PreAuthorize("hasRole('CANDIDATO')")
    public String minhas(@AuthenticationPrincipal UsuarioAutenticado usuario, Model modelo) {
        modelo.addAttribute("candidaturas", minhasCandidaturas(usuario));
        return "minhas-candidaturas";
    }

    private List<CandidaturaResposta> minhasCandidaturas(UsuarioAutenticado usuario) {
        return candidaturas.doCandidato(usuario.entidadeId()).stream().map(CandidaturaResposta::de).toList();
    }

    private Optional<CandidaturaResposta> candidaturaDe(UsuarioAutenticado usuario, Long vagaId) {
        return minhasCandidaturas(usuario).stream()
                .filter(candidatura -> candidatura.vagaId().equals(vagaId))
                .findFirst();
    }

    private static String nomeDoArquivo(MultipartFile arquivo) {
        if (arquivo == null) {
            return "curriculo.pdf";
        }
        String nome = arquivo.getOriginalFilename();
        return nome == null || nome.isBlank() ? "curriculo.pdf" : nome;
    }

    /**
     * Parte ausente vira array vazio de proposito: quem esqueceu de escolher o
     * arquivo recebe a mesma recusa de quem mandou um PDF ilegivel, em vez de
     * um 400 generico do Spring que nao explica nada.
     */
    private static byte[] conteudo(MultipartFile arquivo) {
        if (arquivo == null) {
            return new byte[0];
        }
        try {
            return arquivo.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Erro de negocio em pagina: mostra o recado na propria lista de vagas. */
    static String mensagemDe(ErroDeNegocio erro) {
        return erro.getMessage();
    }
}
