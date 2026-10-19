package com.gupyclone.curriculos;

import com.gupyclone.pessoas.ConsultaDePessoas;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Recebe o curriculo do candidato: valida o arquivo, extrai o texto e guarda o
 * PDF junto com o texto e a origem dele.
 *
 * <p>Deve ser chamado <em>fora</em> de transacao — ver {@link #receber}.
 */
@Service
public class ServicoDeCurriculos implements RecebedorDeCurriculos {

    /** O mesmo limite da coluna {@code curriculo.nome_arquivo}. */
    private static final int MAXIMO_DO_NOME_DE_ARQUIVO = 255;

    private final ConsultaDePessoas pessoas;
    private final CurriculoRepository curriculos;
    private final ExtratorDeTextoDePdf extrator;
    private final TransactionTemplate transacao;

    public ServicoDeCurriculos(ConsultaDePessoas pessoas,
                               CurriculoRepository curriculos,
                               ExtratorDeTextoDePdf extrator,
                               PlatformTransactionManager gerenciadorDeTransacoes) {
        this.pessoas = pessoas;
        this.curriculos = curriculos;
        this.extrator = extrator;
        this.transacao = new TransactionTemplate(gerenciadorDeTransacoes);
    }

    /**
     * {@inheritDoc}
     *
     * <p>A transacao e aberta por {@link TransactionTemplate} em vez de
     * {@code @Transactional} no metodo porque a extracao precisa ficar de fora
     * dela, e quem chama tem que estar fora de transacao para que isso valha.
     */
    @Override
    public Curriculo receber(Long candidatoId, String nomeArquivo, byte[] pdf) {
        if (candidatoId == null) {
            throw new IllegalArgumentException("candidato e obrigatorio para receber um curriculo");
        }
        String nome = nomeValido(nomeArquivo);
        if (pdf == null || pdf.length == 0) {
            throw new CurriculoInvalidoException("o arquivo enviado esta vazio");
        }

        // Fora da transacao de proposito: o OCR de um curriculo digitalizado
        // leva segundos por pagina, e manter uma conexao do pool ocupada por
        // todo esse tempo faria do envio de curriculo o gargalo do sistema.
        // Dentro da transacao fica somente a gravacao.
        TextoExtraido extraido = extrator.extrair(pdf);

        return transacao.execute(status -> {
            // Checagem pela porta, e nao pela chave estrangeira: a violacao de FK
            // chegaria como DataIntegrityViolationException no flush, sem dizer
            // que o id do candidato era o errado. Dentro da transacao da
            // gravacao, como era quando a checagem carregava a entidade.
            if (pessoas.candidato(candidatoId).isEmpty()) {
                throw new IllegalArgumentException("candidato inexistente: " + candidatoId);
            }
            return curriculos.save(new Curriculo(candidatoId, nome, pdf, extraido));
        });
    }

    private static String nomeValido(String nomeArquivo) {
        if (nomeArquivo == null || nomeArquivo.isBlank()) {
            throw new IllegalArgumentException("o arquivo enviado nao tem nome");
        }
        String nome = nomeArquivo.strip();
        if (nome.length() > MAXIMO_DO_NOME_DE_ARQUIVO) {
            throw new IllegalArgumentException(
                    "o nome do arquivo passa de " + MAXIMO_DO_NOME_DE_ARQUIVO + " caracteres");
        }
        return nome;
    }
}
