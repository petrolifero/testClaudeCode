package com.gupyclone.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** A unica pagina que existe para quem nao entrou como ninguem. */
@Controller
public class PaginaInicialController {

    @GetMapping("/")
    public String inicio(@RequestParam(required = false) String aviso, Model modelo) {
        modelo.addAttribute("aviso", Avisos.texto(aviso));
        return "inicio";
    }
}
