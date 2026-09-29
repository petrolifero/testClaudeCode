package br.leds.academico;

import br.leds.comum.aplicacao.Modelagem;
import br.leds.comum.aplicacao.ServicoConsultas;
import br.leds.comum.aplicacao.ServicoImportacao;
import br.leds.comum.cli.Cli;
import br.leds.comum.jdbc.ConfiguracaoBanco;
import br.leds.comum.jdbc.FabricaDataSource;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import javax.sql.DataSource;

/** Raiz de composição da CLI acadêmica: único ponto que conhece as classes concretas. */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        // UTF-8 explícito: acentos saem certos independentemente do locale do terminal
        PrintStream saida = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        PrintStream erro = new PrintStream(System.err, true, StandardCharsets.UTF_8);

        Modelagem modelagem = new ModelagemAcademica();
        DataSource dataSource = FabricaDataSource.simples(ConfiguracaoBanco.doAmbiente(modelagem.nome()));
        Cli cli = Cli.padrao(
                new ServicoConsultas(modelagem.consultas(dataSource)),
                new ServicoImportacao(modelagem.carga(dataSource)));
        System.exit(cli.executar(args, saida, erro));
    }
}
