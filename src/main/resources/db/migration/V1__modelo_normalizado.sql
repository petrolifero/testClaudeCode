-- =====================================================================
-- Modelo "acadêmico": 3ª forma normal, sem atributos multivalorados.
--
-- As listas "Profissões" (candidatos) e "Lista de vagas" (concursos)
-- violariam a 1FN se guardadas numa coluna, então viram relacionamentos
-- N:N com a entidade PROFISSAO através de tabelas associativas.
--
--   ORGAO 1───N CONCURSO N───N PROFISSAO N───N CANDIDATO
--                    (concurso_vaga)   (candidato_profissao)
-- =====================================================================

CREATE TABLE profissao (
    id   integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nome varchar(100) NOT NULL,
    CONSTRAINT uk_profissao_nome UNIQUE (nome)
);

CREATE TABLE orgao (
    id    integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sigla varchar(30) NOT NULL,
    CONSTRAINT uk_orgao_sigla UNIQUE (sigla)
);

CREATE TABLE candidato (
    id              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cpf             char(11)     NOT NULL,
    nome            varchar(200) NOT NULL,
    data_nascimento date         NOT NULL,
    CONSTRAINT uk_candidato_cpf UNIQUE (cpf),  -- também é o índice da busca 1
    CONSTRAINT ck_candidato_cpf CHECK (cpf ~ '^[0-9]{11}$')
);

-- O código NÃO é único nos dados (vários órgãos/editais compartilham o mesmo
-- código) e nem (órgão, edital, código) é, por isso a chave é substituta.
CREATE TABLE concurso (
    id       bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    orgao_id integer     NOT NULL,
    edital   varchar(20) NOT NULL,
    codigo   char(11)    NOT NULL,
    CONSTRAINT fk_concurso_orgao FOREIGN KEY (orgao_id) REFERENCES orgao (id),
    CONSTRAINT ck_concurso_codigo CHECK (codigo ~ '^[0-9]{11}$')
);

CREATE TABLE candidato_profissao (
    candidato_id bigint  NOT NULL,
    profissao_id integer NOT NULL,
    CONSTRAINT pk_candidato_profissao PRIMARY KEY (candidato_id, profissao_id),
    CONSTRAINT fk_cp_candidato FOREIGN KEY (candidato_id) REFERENCES candidato (id) ON DELETE CASCADE,
    CONSTRAINT fk_cp_profissao FOREIGN KEY (profissao_id) REFERENCES profissao (id)
);

CREATE TABLE concurso_vaga (
    concurso_id  bigint  NOT NULL,
    profissao_id integer NOT NULL,
    CONSTRAINT pk_concurso_vaga PRIMARY KEY (concurso_id, profissao_id),
    CONSTRAINT fk_cv_concurso FOREIGN KEY (concurso_id) REFERENCES concurso (id) ON DELETE CASCADE,
    CONSTRAINT fk_cv_profissao FOREIGN KEY (profissao_id) REFERENCES profissao (id)
);

-- ---------------------------------------------------------------------
-- Índices
--
-- As PKs compostas já servem para navegar "de um candidato para suas
-- profissões" e "de um concurso para suas vagas". Para o caminho inverso
-- ("de uma profissão para os candidatos/concursos") criamos os índices
-- com as colunas invertidas. Como contêm as duas colunas, as buscas nas
-- tabelas associativas viram Index Only Scans (não tocam o heap).
-- ---------------------------------------------------------------------
CREATE INDEX ix_candidato_profissao_inv ON candidato_profissao (profissao_id, candidato_id);
CREATE INDEX ix_concurso_vaga_inv       ON concurso_vaga (profissao_id, concurso_id);

-- Busca 2 parte do código do concurso.
CREATE INDEX ix_concurso_codigo ON concurso (codigo);

-- Índice para a FK (evita seq scan em deleções/atualizações de órgão).
CREATE INDEX ix_concurso_orgao ON concurso (orgao_id);
