package com.gupyclone.web;

import com.gupyclone.seguranca.ServicoDePersonas;
import com.gupyclone.seguranca.UsuarioAtual;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Coloca o cabecalho no modelo de toda pagina, para nenhum controlador ter de
 * lembrar de faze-lo (e para nenhuma pagina esquecer o botao de sair).
 *
 * <p>Restrito a {@code @Controller}: a API JSON nao tem cabecalho.
 */
@ControllerAdvice(annotations = Controller.class)
public class CabecalhoNasPaginas {

    private final UsuarioAtual usuarioAtual;
    private final ServicoDePersonas personas;

    public CabecalhoNasPaginas(UsuarioAtual usuarioAtual, ServicoDePersonas personas) {
        this.usuarioAtual = usuarioAtual;
        this.personas = personas;
    }

    @ModelAttribute("cabecalho")
    Cabecalho cabecalho() {
        return usuarioAtual.obter()
                .map(Cabecalho::logado)
                .orElseGet(() -> Cabecalho.deslogado(personas.disponiveis()));
    }
}
