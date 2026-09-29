package br.leds.comum;

/** Utilitários de CPF. Não valida dígitos verificadores: os dados de exemplo são fictícios. */
public final class Cpf {

    private Cpf() {
    }

    /** Remove pontuação e confere que restaram exatamente 11 dígitos. */
    public static String normalizar(String texto) {
        String digitos = texto == null ? "" : texto.replaceAll("\\D", "");
        if (digitos.length() != 11) {
            throw new ErroAplicacao("CPF inválido (esperado 11 dígitos): '" + texto + "'");
        }
        return digitos;
    }

    /** 12345678901 vira 123.456.789-01. */
    public static String formatar(String digitos) {
        return digitos.substring(0, 3) + "." + digitos.substring(3, 6) + "." + digitos.substring(6, 9)
                + "-" + digitos.substring(9);
    }
}
