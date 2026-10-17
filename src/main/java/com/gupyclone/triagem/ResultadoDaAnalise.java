package com.gupyclone.triagem;

import java.util.List;

/**
 * Veredito de uma analise.
 *
 * @param aprovado             se a candidatura avanca
 * @param pontuacao            0 a 100
 * @param motivo               texto que vai no e-mail do candidato
 * @param criteriosAtendidos   descricoes dos criterios atendidos
 * @param criteriosNaoAtendidos descricoes dos criterios nao atendidos
 */
public record ResultadoDaAnalise(
        boolean aprovado,
        int pontuacao,
        String motivo,
        List<String> criteriosAtendidos,
        List<String> criteriosNaoAtendidos) {

    public ResultadoDaAnalise {
        if (pontuacao < 0 || pontuacao > 100) {
            throw new IllegalArgumentException("pontuacao deve estar entre 0 e 100, recebida: " + pontuacao);
        }
        if (motivo == null || motivo.isBlank()) {
            throw new IllegalArgumentException("motivo e obrigatorio: o candidato recebe esse texto por e-mail");
        }
        criteriosAtendidos = List.copyOf(criteriosAtendidos);
        criteriosNaoAtendidos = List.copyOf(criteriosNaoAtendidos);
    }

    public static ResultadoDaAnalise aprovada(int pontuacao, String motivo, List<String> atendidos, List<String> naoAtendidos) {
        return new ResultadoDaAnalise(true, pontuacao, motivo, atendidos, naoAtendidos);
    }

    public static ResultadoDaAnalise reprovada(int pontuacao, String motivo, List<String> atendidos, List<String> naoAtendidos) {
        return new ResultadoDaAnalise(false, pontuacao, motivo, atendidos, naoAtendidos);
    }
}
