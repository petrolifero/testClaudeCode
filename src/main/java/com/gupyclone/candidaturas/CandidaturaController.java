package com.gupyclone.candidaturas;

import com.gupyclone.curriculos.CurriculoInvalidoException;
import com.gupyclone.curriculos.OrigemTexto;
import com.gupyclone.seguranca.UsuarioAutenticado;
import java.io.IOException;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Candidaturas em JSON: o candidato se inscreve e acompanha as proprias, o RH
 * acompanha as da vaga que publicou.
 */
@RestController
public class CandidaturaController {

    /** Candidatura como a API a devolve, igual para o candidato e para o RH. */
    public record CandidaturaResposta(Long id,
                                      Long vagaId,
                                      String tituloDaVaga,
                                      Long candidatoId,
                                      String nomeDoCandidato,
                                      StatusCandidatura status,
                                      Integer pontuacao,
                                      String motivo,
                                      OrigemTexto origemDoTexto,
                                      OffsetDateTime criadoEm,
                                      OffsetDateTime analisadaEm) {

        public static CandidaturaResposta de(Candidatura candidatura) {
            return new CandidaturaResposta(
                    candidatura.getId(),
                    candidatura.getVaga().getId(),
                    candidatura.getVaga().getTitulo(),
                    candidatura.getCandidato().getId(),
                    candidatura.getCandidato().getNome(),
                    candidatura.getStatus(),
                    candidatura.getPontuacao(),
                    candidatura.getMotivo(),
                    candidatura.getCurriculo().getOrigemTexto(),
                    candidatura.getCriadoEm(),
                    candidatura.getAnalisadaEm());
        }
    }

    private final ServicoDeCandidaturas candidaturas;

    public CandidaturaController(ServicoDeCandidaturas candidaturas) {
        this.candidaturas = candidaturas;
    }

    /**
     * O papel tambem e exigido aqui, e nao so na cadeia de filtros: candidato e
     * recrutador tiram id de sequencias independentes e podem coincidir, logo
     * sem esta checagem alguem do RH se candidataria com o id de um candidato.
     *
     * <p>A parte do arquivo e opcional de proposito: faltando, o Spring
     * responderia 400 por conta propria, e quem esqueceu o PDF merece a mesma
     * resposta de quem mandou um PDF ilegivel — 422.
     */
    @PostMapping(path = "/api/vagas/{id}/candidaturas", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('CANDIDATO')")
    public ResponseEntity<CandidaturaResposta> candidatar(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable("id") Long vagaId,
            @RequestPart(name = "arquivo", required = false) MultipartFile arquivo) {
        Candidatura candidatura = candidaturas.candidatar(
                usuario.entidadeId(), vagaId, nomeDe(arquivo), conteudoDe(arquivo));
        return ResponseEntity.created(URI.create("/api/candidaturas/" + candidatura.getId()))
                .body(CandidaturaResposta.de(candidatura));
    }

    @GetMapping("/api/candidaturas/minhas")
    @PreAuthorize("hasRole('CANDIDATO')")
    public List<CandidaturaResposta> minhas(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return candidaturas.doCandidato(usuario.entidadeId()).stream()
                .map(CandidaturaResposta::de)
                .toList();
    }

    @GetMapping("/api/vagas/{id}/candidaturas")
    @PreAuthorize("hasRole('RH')")
    public List<CandidaturaResposta> daVaga(@AuthenticationPrincipal UsuarioAutenticado usuario,
                                            @PathVariable("id") Long vagaId) {
        return candidaturas.daVaga(vagaId, usuario.entidadeId()).stream()
                .map(CandidaturaResposta::de)
                .toList();
    }

    private static byte[] conteudoDe(MultipartFile arquivo) {
        exigirArquivo(arquivo);
        try {
            return arquivo.getBytes();
        } catch (IOException e) {
            throw new CurriculoInvalidoException("não foi possível ler o arquivo enviado", e);
        }
    }

    private static String nomeDe(MultipartFile arquivo) {
        exigirArquivo(arquivo);
        String nome = arquivo.getOriginalFilename();
        // Cliente que manda a parte sem filename existe, e o nome do arquivo e
        // rotulo: nao e motivo para recusar um curriculo que esta legivel.
        return nome == null || nome.isBlank() ? "curriculo.pdf" : nome;
    }

    private static void exigirArquivo(MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new CurriculoInvalidoException("envie o currículo em PDF na parte \"arquivo\"");
        }
    }
}
