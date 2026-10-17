package com.gupyclone.web;

import com.gupyclone.seguranca.Papel;
import com.gupyclone.seguranca.Persona;
import com.gupyclone.seguranca.UsuarioAutenticado;
import java.util.List;

/**
 * O que o canto superior direito mostra.
 *
 * <p>A lista de personas vem VAZIA quando alguem esta autenticado, e nao
 * apenas escondida por CSS ou por um {@code disabled}: a exigencia do
 * enunciado e que "a possibilidade de logar em outro some uma vez que esta
 * logado", entao os formularios de entrar nao devem existir no HTML.
 */
public record Cabecalho(boolean autenticado,
                        String nome,
                        String papel,
                        boolean ehRh,
                        boolean ehCandidato,
                        List<Persona> personas) {

    static Cabecalho deslogado(List<Persona> personas) {
        return new Cabecalho(false, null, null, false, false, List.copyOf(personas));
    }

    static Cabecalho logado(UsuarioAutenticado usuario) {
        return new Cabecalho(true,
                usuario.nome(),
                usuario.papel() == Papel.RH ? "RH" : "Candidato",
                usuario.ehRh(),
                usuario.ehCandidato(),
                List.of());
    }
}
