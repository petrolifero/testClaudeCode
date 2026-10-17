package com.gupyclone.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuracao da triagem automatica.
 *
 * @param estrategia       qual implementacao de analisador usar (ver AnalisadorDeCandidatura)
 * @param intervalo        periodo entre varreduras da fila de analise
 * @param lote             quantos jobs um worker reivindica por varredura
 * @param maxTentativas    apos isso o job vira FALHO e para de ser reprocessado
 * @param esperaAposFalha  atraso aplicado antes de uma nova tentativa
 */
@ConfigurationProperties(prefix = "gupy.triagem")
public record PropriedadesTriagem(
        String estrategia,
        Duration intervalo,
        int lote,
        int maxTentativas,
        Duration esperaAposFalha) {
}
