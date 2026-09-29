package br.org.leds.concursos.dominio;

/**
 * Código de um concurso público: 11 dígitos numéricos.
 *
 * <p>Atenção: nos dados fornecidos o código <strong>não</strong> identifica um único concurso
 * (há vários órgãos/editais com o mesmo código), por isso ele é tratado como um atributo de
 * busca e não como chave.</p>
 *
 * @param valor os 11 dígitos do código
 */
public record CodigoConcurso(String valor) {

    public CodigoConcurso {
        if (valor == null || !valor.strip().matches("\\d{11}")) {
            throw new DadoInvalidoException("Código do concurso deve conter exatamente 11 dígitos: " + valor);
        }
        valor = valor.strip();
    }

    public static CodigoConcurso of(String valor) {
        return new CodigoConcurso(valor);
    }

    @Override
    public String toString() {
        return valor;
    }
}
