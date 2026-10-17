package com.gupyclone.seguranca;

/**
 * Quem esta autenticado no momento. E o principal do Spring Security, portanto
 * controladores podem receber isso direto com {@code @AuthenticationPrincipal}.
 *
 * @param chave      identificador da persona ("ana", "carlos", "joao")
 * @param nome       nome para exibicao
 * @param papel      CANDIDATO ou RH
 * @param entidadeId id do candidato ou do recrutador correspondente
 */
public record UsuarioAutenticado(String chave, String nome, Papel papel, Long entidadeId) {

    public boolean ehRh() {
        return papel == Papel.RH;
    }

    public boolean ehCandidato() {
        return papel == Papel.CANDIDATO;
    }
}
