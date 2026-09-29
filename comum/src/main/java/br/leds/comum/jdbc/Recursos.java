package br.leds.comum.jdbc;

import br.leds.comum.erro.ErroInfraestrutura;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Leitura de arquivos empacotados no classpath (ex.: {@code schema.sql}). */
public final class Recursos {

    private Recursos() {
    }

    public static String lerTexto(Class<?> referencia, String caminho) {
        try (InputStream in = referencia.getResourceAsStream(caminho)) {
            if (in == null) {
                throw new ErroInfraestrutura("Recurso ausente: " + caminho);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ErroInfraestrutura("Falha ao ler " + caminho, e);
        }
    }
}
