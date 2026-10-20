package com.gupyclone.candidaturas;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Porta de candidaturas atendida pelo repositorio do proprio modulo. */
// Classe propria em vez de o CandidaturaRepository implementar a porta: assim o
// repositorio continua detalhe interno de candidaturas e quem conclui uma
// candidatura do lado de fora nao recebe as escritas do JpaRepository de brinde.
@Component
public class CandidaturasJpa implements Candidaturas {

    private final CandidaturaRepository candidaturas;

    public CandidaturasJpa(CandidaturaRepository candidaturas) {
        this.candidaturas = candidaturas;
    }

    @Override
    public Optional<DadosParaAnalise> paraAnalise(Long candidaturaId) {
        return candidaturas.dadosParaAnalise(candidaturaId);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Exige a transacao de quem chamou: ver a escolha de propagacao no codigo.
     */
    // MANDATORY, como na fila de e-mails e pelo mesmo motivo: a conclusao e o
    // aviso ao candidato sao gravados juntos ou nenhum dos dois. Um chamador sem
    // transacao e recusado aqui, na primeira escrita, em vez de concluir a
    // candidatura e so depois descobrir que o aviso nao tem onde ser gravado.
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean concluir(Long id, StatusCandidatura status, int pontuacao, String motivo) {
        return candidaturas.concluir(id, status, pontuacao, motivo, OffsetDateTime.now()) == 1;
    }
}
