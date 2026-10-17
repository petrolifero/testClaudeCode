package com.gupyclone.vagas;

import com.gupyclone.seguranca.UsuarioAutenticado;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Vagas em JSON: o RH publica, qualquer pessoa autenticada consulta. */
@RestController
@RequestMapping("/api/vagas")
public class VagaController {

    /** Criterio de aceite como o RH o envia. */
    public record CriterioRequisicao(
            @NotBlank(message = "não pode ficar em branco")
            @Size(max = 200, message = "passa de 200 caracteres")
            String descricao,

            @NotBlank(message = "não pode ficar em branco")
            @Size(max = 200, message = "passa de 200 caracteres")
            String expressao,

            @Min(value = 1, message = "deve estar entre 1 e 100")
            @Max(value = 100, message = "deve estar entre 1 e 100")
            int peso,

            boolean obrigatorio) {
    }

    /**
     * Vaga nova.
     *
     * <p>Nao existe campo de recrutador de proposito: o dono da vaga vem do
     * token, nunca do corpo da requisicao.
     */
    public record NovaVagaRequisicao(
            @NotBlank(message = "não pode ficar em branco")
            @Size(max = 160, message = "passa de 160 caracteres")
            String titulo,

            @NotBlank(message = "não pode ficar em branco")
            String descricao,

            @Min(value = 0, message = "deve estar entre 0 e 100")
            @Max(value = 100, message = "deve estar entre 0 e 100")
            int pontuacaoMinima,

            @Valid List<CriterioRequisicao> criterios) {

        List<ServicoDeVagas.NovoCriterio> paraOServico() {
            return criterios == null ? List.of() : criterios.stream()
                    .map(criterio -> new ServicoDeVagas.NovoCriterio(
                            criterio.descricao(), criterio.expressao(),
                            criterio.peso(), criterio.obrigatorio()))
                    .toList();
        }
    }

    /** Vaga como a API a devolve. */
    public record VagaResposta(Long id,
                               String titulo,
                               String descricao,
                               StatusVaga status,
                               int pontuacaoMinima,
                               RecrutadorResposta recrutador,
                               List<CriterioResposta> criterios,
                               OffsetDateTime criadoEm) {

        /** Quem publicou a vaga, o suficiente para exibir. */
        public record RecrutadorResposta(Long id, String nome) {
        }

        /** Criterio de aceite ja gravado. */
        public record CriterioResposta(Long id, String descricao, String expressao, int peso, boolean obrigatorio) {
        }

        public static VagaResposta de(Vaga vaga) {
            return new VagaResposta(
                    vaga.getId(),
                    vaga.getTitulo(),
                    vaga.getDescricao(),
                    vaga.getStatus(),
                    vaga.getPontuacaoMinima(),
                    new RecrutadorResposta(vaga.getRecrutador().getId(), vaga.getRecrutador().getNome()),
                    vaga.getCriterios().stream()
                            .map(criterio -> new CriterioResposta(criterio.getId(), criterio.getDescricao(),
                                    criterio.getExpressao(), criterio.getPeso(), criterio.isObrigatorio()))
                            .toList(),
                    vaga.getCriadoEm());
        }
    }

    private final ServicoDeVagas vagas;

    public VagaController(ServicoDeVagas vagas) {
        this.vagas = vagas;
    }

    /**
     * O papel tambem e exigido aqui, e nao so na cadeia de filtros: o id da
     * entidade de um candidato poderia coincidir com o de um recrutador, e
     * sem esta checagem um candidato publicaria em nome de alguem do RH.
     */
    @PostMapping
    @PreAuthorize("hasRole('RH')")
    public ResponseEntity<VagaResposta> publicar(@AuthenticationPrincipal UsuarioAutenticado usuario,
                                                 @Valid @RequestBody NovaVagaRequisicao requisicao) {
        Vaga vaga = vagas.publicar(usuario.entidadeId(), requisicao.titulo(), requisicao.descricao(),
                requisicao.pontuacaoMinima(), requisicao.paraOServico());
        return ResponseEntity.created(URI.create("/api/vagas/" + vaga.getId()))
                .body(VagaResposta.de(vaga));
    }

    @GetMapping
    public List<VagaResposta> abertas() {
        return vagas.abertas().stream().map(VagaResposta::de).toList();
    }

    @GetMapping("/{id}")
    public VagaResposta porId(@PathVariable Long id) {
        return VagaResposta.de(vagas.porId(id));
    }
}
