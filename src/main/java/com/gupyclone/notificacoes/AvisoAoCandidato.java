package com.gupyclone.notificacoes;

/**
 * O que se precisa saber para avisar o candidato: ja em forma de dados, sem
 * nenhuma entidade de outro modulo.
 *
 * <p>Quem monta o aviso e a triagem, que tem esses dados em mao na transacao em
 * que decide a candidatura. E por isto que notificacoes nao conhece
 * {@code Candidatura}: daqui para baixo, uma candidatura e um id.
 *
 * @param destinatario endereco de e-mail do candidato
 * @param pontuacao    nota da analise, de 0 a 100
 * @param motivo       texto da decisao, que vai no corpo do e-mail
 */
public record AvisoAoCandidato(Long candidaturaId, TipoEmail tipo, String destinatario,
                               String nomeDoCandidato, String tituloDaVaga,
                               int pontuacao, String motivo) {
}
