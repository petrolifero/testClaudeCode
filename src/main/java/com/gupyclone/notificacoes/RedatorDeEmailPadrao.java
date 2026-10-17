package com.gupyclone.notificacoes;

import com.gupyclone.candidaturas.Candidatura;
import org.springframework.stereotype.Component;

/**
 * Texto padrao dos avisos ao candidato.
 *
 * <p>Precisa ser chamado com a candidatura gerenciada, dentro da transacao que
 * a concluiu: le o candidato e a vaga, que sao associacoes preguicosas.
 */
@Component
public class RedatorDeEmailPadrao implements RedatorDeEmail {

    private static final String ASSINATURA = """

            Um abraço,
            Equipe de Recrutamento""";

    /**
     * A triagem lista os criterios nao atendidos, mas uma vaga pode nao ter
     * criterio nenhum: nesse caso o que sobra e a pontuacao minima.
     */
    private static final String MOTIVO_AUSENTE =
            "a análise do seu currículo não alcançou a pontuação mínima definida para a vaga.";

    @Override
    public EmailOutbox redigir(Candidatura candidatura, TipoEmail tipo, int pontuacao, String motivo) {
        if (candidatura == null) {
            throw new IllegalArgumentException("sem candidatura nao ha o que avisar");
        }
        if (tipo == null) {
            throw new IllegalArgumentException("o aviso precisa de um tipo");
        }
        String nome = candidatura.getCandidato().getNome();
        String vaga = candidatura.getVaga().getTitulo();
        return switch (tipo) {
            case CANDIDATURA_APROVADA -> new EmailOutbox(candidatura,
                    tipo,
                    candidatura.getCandidato().getEmail(),
                    "Sua candidatura avançou: " + vaga,
                    aprovada(nome, vaga, pontuacao));
            case CANDIDATURA_REPROVADA -> new EmailOutbox(candidatura,
                    tipo,
                    candidatura.getCandidato().getEmail(),
                    "Sobre sua candidatura: " + vaga,
                    reprovada(nome, vaga, pontuacao, motivo));
        };
    }

    private static String aprovada(String nome, String vaga, int pontuacao) {
        return """
                Olá, %s!

                Boa notícia: sua candidatura para a vaga "%s" foi aprovada na triagem e
                avançou para a próxima etapa do processo seletivo.

                Sua pontuação nos critérios de aceite da vaga foi %d de 100.

                Em breve alguém do time de recrutamento entra em contato com você para
                combinar os próximos passos.
                """.formatted(nome, vaga, pontuacao) + ASSINATURA;
    }

    private static String reprovada(String nome, String vaga, int pontuacao, String motivo) {
        return """
                Olá, %s!

                Obrigado por se candidatar à vaga "%s" e pelo tempo que você dedicou a isso.

                Depois de analisar seu currículo, sua candidatura não avançou nesta etapa.
                Sua pontuação nos critérios de aceite da vaga foi %d de 100.

                Motivo: %s

                Isso diz respeito ao encaixe com os requisitos desta vaga específica, e não
                ao seu valor como profissional. Seu cadastro continua com a gente: fique à
                vontade para se candidatar às nossas próximas vagas.
                """.formatted(nome, vaga, pontuacao, motivoLegivel(motivo)) + ASSINATURA;
    }

    private static String motivoLegivel(String motivo) {
        return motivo == null || motivo.isBlank() ? MOTIVO_AUSENTE : motivo.strip();
    }
}
