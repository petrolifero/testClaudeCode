package com.gupyclone.seguranca;

/**
 * Uma das pessoas que aparecem no canto superior direito quando ninguem esta
 * autenticado.
 */
public record Persona(String chave, String nome, Papel papel) {
}
