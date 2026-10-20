package com.gupyclone.curriculos;

import com.gupyclone.config.PropriedadesOcr;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * OCR chamando o binario do Tesseract por linha de comando.
 *
 * <p>Processo externo em vez do Tess4J de proposito: o lept4j que o Tess4J
 * empacota exige uma Leptonica mais nova que a do sistema e estoura
 * {@code UnsatisfiedLinkError} em tempo de execucao. Um processo nao tem
 * acoplamento de ABI; o preco e um fork por pagina, irrelevante perto do custo
 * do proprio reconhecimento.
 */
@Component
public class MotorDeOcrTesseract implements MotorDeOcr {

    private static final Logger log = LoggerFactory.getLogger(MotorDeOcrTesseract.class);

    /**
     * Generoso de proposito: uma pagina A4 a 300 DPI leva segundos, e o limite
     * existe apenas para que um tesseract travado nao prenda a requisicao para
     * sempre.
     */
    private static final Duration LIMITE_POR_PAGINA = Duration.ofMinutes(2);

    private static final String COMO_INSTALAR =
            "Instale o OCR com: apt-get install -y tesseract-ocr tesseract-ocr-por";

    private final PropriedadesOcr propriedades;

    public MotorDeOcrTesseract(PropriedadesOcr propriedades) {
        this.propriedades = propriedades;
    }

    @Override
    public String reconhecer(BufferedImage imagem) {
        if (imagem == null) {
            throw new IllegalArgumentException("nao ha imagem para reconhecer");
        }
        Path entrada = null;
        Path saida = null;
        Path diagnostico = null;
        try {
            entrada = Files.createTempFile("gupy-ocr-", ".png");
            saida = Files.createTempFile("gupy-ocr-", ".txt");
            diagnostico = Files.createTempFile("gupy-ocr-", ".log");
            if (!ImageIO.write(imagem, "png", entrada.toFile())) {
                throw new OcrIndisponivelException("nenhum codificador de PNG disponivel para o OCR");
            }
            return executarTesseract(entrada, saida, diagnostico);
        } catch (IOException e) {
            throw new UncheckedIOException("falha ao preparar os arquivos temporarios do OCR", e);
        } finally {
            apagar(entrada, saida, diagnostico);
        }
    }

    private String executarTesseract(Path entrada, Path saida, Path diagnostico) throws IOException {
        Process processo = iniciar(entrada, saida, diagnostico);
        try {
            // O tesseract le o PNG do disco e nunca o stdin; fechar o pipe
            // evita um descritor aberto por pagina processada.
            processo.getOutputStream().close();
            if (!processo.waitFor(LIMITE_POR_PAGINA.toSeconds(), TimeUnit.SECONDS)) {
                throw new OcrIndisponivelException("o OCR de uma pagina passou de "
                        + LIMITE_POR_PAGINA.toSeconds() + "s e foi abortado");
            }
            String avisos = conteudoDe(diagnostico).strip();
            if (processo.exitValue() != 0) {
                // Na pratica o motivo quase sempre e pacote de idioma ausente,
                // por isso o erro ja vem com a receita de instalacao.
                throw new OcrIndisponivelException("tesseract terminou com codigo "
                        + processo.exitValue() + ": " + avisos + ". " + COMO_INSTALAR);
            }
            // O tesseract escreve avisos uteis ("Empty page!!", por exemplo) no
            // stderr mesmo quando termina bem. Vao para o log e nunca para o
            // texto do curriculo.
            if (!avisos.isEmpty()) {
                log.debug("tesseract: {}", avisos);
            }
            return conteudoDe(saida);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OcrIndisponivelException("OCR interrompido antes de terminar", e);
        } finally {
            processo.destroyForcibly();
        }
    }

    private Process iniciar(Path entrada, Path saida, Path diagnostico) throws IOException {
        List<String> comando = comando(entrada);
        try {
            return new ProcessBuilder(comando)
                    // Saida e erro em arquivos separados: ler o stdout por pipe
                    // enquanto o stderr enche o buffer do sistema e o caminho
                    // classico para o processo travar; e o stderr do tesseract
                    // nao pode se misturar ao texto reconhecido.
                    .redirectOutput(saida.toFile())
                    .redirectError(diagnostico.toFile())
                    .start();
        } catch (IOException e) {
            throw new OcrIndisponivelException(
                    "nao foi possivel executar " + String.join(" ", comando) + ": " + e.getMessage()
                            + ". " + COMO_INSTALAR, e);
        }
    }

    private List<String> comando(Path entrada) {
        List<String> comando = new ArrayList<>(List.of(propriedades.executavel(), entrada.toString(), "stdout"));
        if (preenchido(propriedades.idiomas())) {
            comando.add("-l");
            comando.add(propriedades.idiomas().strip());
        }
        // Sem --tessdata-dir o tesseract usa o diretorio do sistema, que e o
        // comportamento desejavel; apontar para um caminho que nao existe seria
        // pior do que nao apontar.
        if (preenchido(propriedades.datapath()) && Files.isDirectory(Path.of(propriedades.datapath().strip()))) {
            comando.add("--tessdata-dir");
            comando.add(propriedades.datapath().strip());
        }
        return comando;
    }

    /** Bytes invalidos do OCR viram U+FFFD em vez de derrubar a leitura. */
    private static String conteudoDe(Path arquivo) throws IOException {
        return new String(Files.readAllBytes(arquivo), StandardCharsets.UTF_8);
    }

    private static boolean preenchido(String valor) {
        return valor != null && !valor.isBlank();
    }

    private static void apagar(Path... temporarios) {
        for (Path temporario : temporarios) {
            if (temporario == null) {
                continue;
            }
            try {
                Files.deleteIfExists(temporario);
            } catch (IOException e) {
                log.warn("temporario de OCR nao apagado: {}", temporario, e);
            }
        }
    }
}
