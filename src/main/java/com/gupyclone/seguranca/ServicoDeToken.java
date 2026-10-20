package com.gupyclone.seguranca;

import com.gupyclone.config.PropriedadesJwt;
import java.time.Instant;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;

/**
 * Emite e le o JWT que representa "estar logado como" uma persona.
 *
 * <p>Nao existe login: clicar em uma pessoa ja autoriza. O token carrega o
 * papel e o id da entidade, e e assinado em HS256 com o segredo da aplicacao.
 */
@Service
public class ServicoDeToken {

    static final String CLAIM_NOME = "nome";
    static final String CLAIM_PAPEL = "papel";
    static final String CLAIM_ENTIDADE = "entidadeId";

    private final JwtEncoder codificador;
    private final JwtDecoder decodificador;
    private final PropriedadesJwt propriedades;

    public ServicoDeToken(JwtEncoder codificador, JwtDecoder decodificador, PropriedadesJwt propriedades) {
        this.codificador = codificador;
        this.decodificador = decodificador;
        this.propriedades = propriedades;
    }

    public String emitir(UsuarioAutenticado usuario) {
        Instant agora = Instant.now();
        JwtClaimsSet reivindicacoes = JwtClaimsSet.builder()
                .issuer("gupy-clone")
                .issuedAt(agora)
                .expiresAt(agora.plus(propriedades.validade()))
                .subject(usuario.chave())
                .claim(CLAIM_NOME, usuario.nome())
                .claim(CLAIM_PAPEL, usuario.papel().name())
                .claim(CLAIM_ENTIDADE, usuario.entidadeId())
                .build();
        JwsHeader cabecalho = JwsHeader.with(MacAlgorithm.HS256).build();
        return codificador.encode(JwtEncoderParameters.from(cabecalho, reivindicacoes)).getTokenValue();
    }

    public UsuarioAutenticado ler(String token) {
        try {
            Jwt jwt = decodificador.decode(token);
            Papel papel = Papel.valueOf(jwt.getClaimAsString(CLAIM_PAPEL));
            Long entidadeId = jwt.getClaim(CLAIM_ENTIDADE) instanceof Number numero ? numero.longValue() : null;
            if (entidadeId == null) {
                throw new TokenInvalidoException("token sem " + CLAIM_ENTIDADE);
            }
            return new UsuarioAutenticado(jwt.getSubject(), jwt.getClaimAsString(CLAIM_NOME), papel, entidadeId);
        } catch (JwtException | IllegalArgumentException | NullPointerException e) {
            throw new TokenInvalidoException("token invalido", e);
        }
    }
}
