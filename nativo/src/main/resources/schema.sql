-- Solução NATIVA: as listas do CSV viram text[] e o índice GIN faz o trabalho das tabelas de associação.

CREATE TABLE IF NOT EXISTS candidato (
    cpf             char(11) PRIMARY KEY,              -- índice da consulta por CPF
    nome            text     NOT NULL,
    data_nascimento date     NOT NULL,
    profissoes      text[]   NOT NULL
);
-- acelera "profissoes && ARRAY[...]" e "profissoes @> ..." (consulta por código de concurso)
CREATE INDEX IF NOT EXISTS candidato_profissoes_gin ON candidato USING gin (profissoes);

CREATE TABLE IF NOT EXISTS concurso (
    id     bigint PRIMARY KEY,
    orgao  text   NOT NULL,
    edital text   NOT NULL,
    codigo bigint NOT NULL,                            -- NÃO é único nos dados
    vagas  text[] NOT NULL
);
CREATE INDEX IF NOT EXISTS concurso_codigo_idx ON concurso (codigo);
-- acelera a consulta por CPF: concursos cujas vagas se sobrepõem às profissões do candidato
CREATE INDEX IF NOT EXISTS concurso_vagas_gin ON concurso USING gin (vagas);
