package com.gupyclone.triagem;

/**
 * A analise nao pode ser feita agora: a LLM nao respondeu, respondeu fora do
 * formato combinado, ou devolveu erro.
 *
 * <p>Irma de {@code OcrIndisponivelException} e, como ela, nao e mapeada para
 * um 4xx de proposito: o curriculo do candidato pode estar perfeito e ainda
 * assim a analise falhar, porque a falha e de infraestrutura nossa. Quem a
 * recebe e o {@link ProcessadorDeAnalises}, que devolve o job para a fila em
 * vez de inventar um veredito — nunca aprovar nem reprovar por engano.
 */
public class AnaliseIndisponivelException extends RuntimeException {

    public AnaliseIndisponivelException(String mensagem) {
        super(mensagem);
    }

    public AnaliseIndisponivelException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
