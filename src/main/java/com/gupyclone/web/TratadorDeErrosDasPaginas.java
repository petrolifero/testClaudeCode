package com.gupyclone.web;

import com.gupyclone.comum.ErroDeNegocio;
import com.gupyclone.seguranca.PersonaInexistenteException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * Traduz erro de dominio em pagina, nao em JSON.
 *
 * <p>Existe porque {@code TratadorDeErrosDaApi} e
 * {@code @RestControllerAdvice(annotations = RestController.class)}: ele cobre
 * so a API. No caminho HTML as mesmas excecoes subiam sem tratamento e viravam
 * 500 — {@code POST /auth/entrar/chave-que-nao-existe} devolvia "Internal
 * Server Error" onde cabia um recado. A revisao adversarial apontou isso e
 * ficou sem conserto na primeira passada.
 *
 * <p>Restrito a {@code @Controller} para nao interceptar a API, que tem o seu
 * proprio tradutor e um contrato de corpo diferente.
 */
@ControllerAdvice(annotations = Controller.class)
public class TratadorDeErrosDasPaginas {

    private static final Logger log = LoggerFactory.getLogger(TratadorDeErrosDasPaginas.class);

    /**
     * Persona desconhecida volta para a inicial com recado, em vez de pagina de
     * erro: pela interface isso so acontece com requisicao forjada, e a inicial
     * e justamente onde estao as pessoas que existem.
     */
    @ExceptionHandler(PersonaInexistenteException.class)
    public String personaInexistente(PersonaInexistenteException e) {
        log.debug("tentativa de entrar como persona inexistente: {}", e.getMessage());
        return "redirect:/?aviso=pessoa-desconhecida";
    }

    /** Qualquer outro erro de negocio vira a pagina de erro com o status dele. */
    @ExceptionHandler(ErroDeNegocio.class)
    public String erroDeNegocio(ErroDeNegocio e, Model modelo,
                                jakarta.servlet.http.HttpServletResponse resposta) {
        resposta.setStatus(e.getStatus().value());
        modelo.addAttribute("status", e.getStatus().value());
        modelo.addAttribute("error", e.getMessage());
        return "error";
    }
}
