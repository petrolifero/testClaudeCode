package br.org.leds.concursos.infra.csv;

import br.org.leds.concursos.aplicacao.ImportacaoRepository;
import br.org.leds.concursos.dominio.Candidato;
import br.org.leds.concursos.dominio.Concurso;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.BiFunction;

/**
 * Carrega os arquivos de entrada no banco na primeira inicialização (banco vazio).
 * A carga ocorre numa única transação: ou tudo é gravado, ou nada.
 */
@Component
public class ImportadorInicial implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(ImportadorInicial.class);

    private final ImportacaoProperties propriedades;
    private final LeitorArquivos leitor;
    private final ImportacaoRepository repository;
    private final TransactionTemplate transacao;

    public ImportadorInicial(ImportacaoProperties propriedades, LeitorArquivos leitor,
                             ImportacaoRepository repository, TransactionTemplate transacao) {
        this.propriedades = propriedades;
        this.leitor = leitor;
        this.repository = repository;
        this.transacao = transacao;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!propriedades.habilitada()) {
            LOG.info("Importação inicial desabilitada");
            return;
        }
        if (repository.possuiDados()) {
            LOG.info("Banco já possui dados; importação inicial ignorada");
            return;
        }
        importar(propriedades.candidatos(), propriedades.concursos());
    }

    /** Lê os dois arquivos e grava tudo numa única transação. */
    public void importar(Resource arquivoCandidatos, Resource arquivoConcursos) {
        List<Candidato> candidatos = ler(arquivoCandidatos, leitor::lerCandidatos);
        List<Concurso> concursos = ler(arquivoConcursos, leitor::lerConcursos);
        transacao.executeWithoutResult(status -> {
            repository.salvarCandidatos(candidatos);
            repository.salvarConcursos(concursos);
        });
        LOG.info("Importados {} candidatos e {} concursos", candidatos.size(), concursos.size());
    }

    private <T> List<T> ler(Resource arquivo, BiFunction<Reader, String, List<T>> conversor) {
        String nome = arquivo.getDescription();
        try (Reader reader = new InputStreamReader(arquivo.getInputStream(), StandardCharsets.UTF_8)) {
            return conversor.apply(reader, nome);
        } catch (IOException e) {
            throw new ArquivoInvalidoException(nome, "não foi possível abrir o arquivo", e);
        }
    }
}
