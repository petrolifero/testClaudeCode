-- Solução ACADÊMICA: 3ª forma normal, sem arrays. Listas viram tabelas de associação (N:N).

CREATE TABLE IF NOT EXISTS profissao (
    id   bigint PRIMARY KEY,
    nome text   NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS candidato (
    id              bigint PRIMARY KEY,
    nome            text     NOT NULL,
    data_nascimento date     NOT NULL,
    cpf             char(11) NOT NULL UNIQUE          -- índice da consulta por CPF
);

CREATE TABLE IF NOT EXISTS concurso (
    id     bigint PRIMARY KEY,
    orgao  text   NOT NULL,
    edital text   NOT NULL,
    codigo bigint NOT NULL                             -- NÃO é único nos dados
);
CREATE INDEX IF NOT EXISTS concurso_codigo_idx ON concurso (codigo);  -- consulta por código

CREATE TABLE IF NOT EXISTS candidato_profissao (
    candidato_id bigint NOT NULL REFERENCES candidato (id) ON DELETE CASCADE,
    profissao_id bigint NOT NULL REFERENCES profissao (id),
    PRIMARY KEY (candidato_id, profissao_id)           -- candidato -> profissões
);
-- sentido inverso (profissão -> candidatos); inclui candidato_id, permitindo index-only scan
CREATE INDEX IF NOT EXISTS candidato_profissao_inv_idx ON candidato_profissao (profissao_id, candidato_id);

CREATE TABLE IF NOT EXISTS concurso_vaga (
    concurso_id  bigint NOT NULL REFERENCES concurso (id) ON DELETE CASCADE,
    profissao_id bigint NOT NULL REFERENCES profissao (id),
    PRIMARY KEY (concurso_id, profissao_id)            -- concurso -> vagas
);
-- sentido inverso (profissão -> concursos)
CREATE INDEX IF NOT EXISTS concurso_vaga_inv_idx ON concurso_vaga (profissao_id, concurso_id);
