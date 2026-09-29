package br.org.leds.concursos.dominio;

import java.util.regex.Pattern;

/**
 * CPF validado (formato e dígitos verificadores).
 *
 * <p>Aceita tanto a forma formatada ({@code 182.845.084-34}) quanto só os dígitos
 * ({@code 18284508434}). Internamente guarda apenas os 11 dígitos.</p>
 *
 * @param digitos os 11 dígitos do CPF, sem pontuação
 */
public record Cpf(String digitos) {

    private static final Pattern FORMATO_ACEITO = Pattern.compile("\\d{3}\\.?\\d{3}\\.?\\d{3}-?\\d{2}");
    private static final int TAMANHO = 11;

    public Cpf {
        if (digitos == null || !digitos.matches("\\d{11}")) {
            throw new DadoInvalidoException("CPF deve conter exatamente 11 dígitos: " + digitos);
        }
        if (todosDigitosIguais(digitos) || !digitosVerificadoresConferem(digitos)) {
            throw new DadoInvalidoException("CPF com dígitos verificadores inválidos: " + digitos);
        }
    }

    /**
     * Cria um CPF a partir de texto formatado ou não.
     *
     * @throws DadoInvalidoException se o texto não for um CPF válido
     */
    public static Cpf of(String texto) {
        if (texto == null || !FORMATO_ACEITO.matcher(texto.strip()).matches()) {
            throw new DadoInvalidoException("CPF em formato inválido: " + texto);
        }
        return new Cpf(texto.replaceAll("\\D", ""));
    }

    /** Representação no formato {@code 000.000.000-00}, a mesma usada nos arquivos de entrada. */
    public String formatado() {
        return "%s.%s.%s-%s".formatted(
                digitos.substring(0, 3), digitos.substring(3, 6), digitos.substring(6, 9), digitos.substring(9));
    }

    @Override
    public String toString() {
        return formatado();
    }

    private static boolean todosDigitosIguais(String d) {
        return d.chars().allMatch(c -> c == d.charAt(0));
    }

    private static boolean digitosVerificadoresConferem(String d) {
        return digitoVerificador(d, 9) == d.charAt(9) - '0'
                && digitoVerificador(d, 10) == d.charAt(10) - '0';
    }

    /** Calcula o dígito verificador na posição {@code quantidade} usando os dígitos anteriores. */
    private static int digitoVerificador(String d, int quantidade) {
        int soma = 0;
        for (int i = 0; i < quantidade; i++) {
            soma += (d.charAt(i) - '0') * (quantidade + 1 - i);
        }
        int resto = (soma * 10) % TAMANHO;
        return resto == 10 ? 0 : resto;
    }
}
