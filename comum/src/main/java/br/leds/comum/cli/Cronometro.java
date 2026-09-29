package br.leds.comum.cli;

final class Cronometro {

    private Cronometro() {
    }

    static long milissegundos(long inicioNanos) {
        return (System.nanoTime() - inicioNanos) / 1_000_000;
    }
}
