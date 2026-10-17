package com.gupyclone.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuracao do cliente de LLM usado pela estrategia de triagem "llm". */
@ConfigurationProperties(prefix = "gupy.llm")
public record PropriedadesLlm(String baseUrl, String apiKey, String modelo, Duration timeout) {
}
