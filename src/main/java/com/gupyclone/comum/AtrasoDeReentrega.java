package com.gupyclone.comum;

import java.time.Duration;

/**
 * Quanto esperar antes de tentar de novo um item de fila que falhou.
 *
 * <p>Exponencial, e nao fixo, porque com atraso fixo o orcamento de tentativas
 * se esgota em segundos: com espera de 5s e 3 tentativas, uma indisponibilidade
 * de 10 segundos bastava para marcar o item como FALHO. Dobrando a cada
 * tentativa, as mesmas 3 tentativas cobrem minutos, que e a duracao tipica de
 * uma falha transitoria de rede ou de provedor.
 *
 * <p>Vive em {@code comum} porque as duas filas (analise e e-mail) seguem a
 * mesma politica e nenhuma delas e dona dela.
 */
public final class AtrasoDeReentrega {

    /** Teto, para a espera nao virar atraso de horas numa fila so de retentativa. */
    private static final Duration TETO = Duration.ofMinutes(10);

    private static final int MAXIMO_DE_DOBRAS = 8;

    private AtrasoDeReentrega() {
    }

    /**
     * @param espera     atraso base configurado
     * @param tentativas quantas tentativas o item ja consumiu
     */
    public static Duration apos(Duration espera, int tentativas) {
        long fator = 1L << Math.min(Math.max(tentativas - 1, 0), MAXIMO_DE_DOBRAS);
        Duration atraso = espera.multipliedBy(fator);
        return atraso.compareTo(TETO) > 0 ? TETO : atraso;
    }
}
