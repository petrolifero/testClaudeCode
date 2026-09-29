-- =====================================================================
-- Modelo "PostgreSQL de verdade": uma tabela por arquivo, com as mesmas
-- colunas e a mesma tipagem do CSV. As listas "Profissões" e "Lista de
-- vagas" são arrays nativos (text[]) e as regras de formato ficam em
-- DOMAINs, validadas pelo próprio banco.
--
-- O casamento candidato x concurso é a sobreposição de arrays (&&),
-- acelerada por índices GIN, que indexam cada elemento do array.
-- =====================================================================

-- CPF exatamente como no arquivo: 000.000.000-00
CREATE DOMAIN cpf AS text
    CHECK (VALUE ~ '^[0-9]{3}\.[0-9]{3}\.[0-9]{3}-[0-9]{2}$');

-- Código do concurso: 11 dígitos (não é único!)
CREATE DOMAIN codigo_concurso AS text
    CHECK (VALUE ~ '^[0-9]{11}$');

-- Lista de profissões: não vazia e sem elementos nulos
CREATE DOMAIN lista_profissoes AS text[]
    CHECK (cardinality(VALUE) > 0 AND array_position(VALUE, NULL) IS NULL);

CREATE TABLE candidatos (
    nome            text             NOT NULL,
    data_nascimento date             NOT NULL,
    cpf             cpf              NOT NULL,
    profissoes      lista_profissoes NOT NULL,
    CONSTRAINT pk_candidatos PRIMARY KEY (cpf)          -- índice da busca 1
);

-- Nem o código nem (órgão, edital, código) são únicos nos dados: chave substituta.
CREATE TABLE concursos (
    id     bigint GENERATED ALWAYS AS IDENTITY,
    orgao  text             NOT NULL,
    edital text             NOT NULL,
    codigo codigo_concurso  NOT NULL,
    vagas  lista_profissoes NOT NULL,
    CONSTRAINT pk_concursos PRIMARY KEY (id)
);

-- ---------------------------------------------------------------------
-- Índices
-- ---------------------------------------------------------------------

-- Busca 1: "concursos cujas vagas se sobrepõem às profissões do candidato".
-- GIN com array_ops indexa cada profissão -> lista de concursos (posting list),
-- atendendo os operadores && (sobreposição), @> (contém) e <@ (contido).
CREATE INDEX ix_concursos_vagas ON concursos USING gin (vagas);

-- Busca 2: "candidatos cujas profissões se sobrepõem às vagas do código".
CREATE INDEX ix_candidatos_profissoes ON candidatos USING gin (profissoes);

-- Busca 2 também parte do código. O INCLUDE (vagas) permite um Index Only Scan:
-- as vagas do código saem direto do índice, sem visitar a tabela.
CREATE INDEX ix_concursos_codigo ON concursos (codigo) INCLUDE (vagas);

-- Estatísticas por elemento de array (most_common_elems) mais precisas ajudam o
-- planejador a decidir entre GIN e seq scan quando a sobreposição é pouco seletiva.
ALTER TABLE candidatos ALTER COLUMN profissoes SET STATISTICS 1000;
ALTER TABLE concursos  ALTER COLUMN vagas      SET STATISTICS 1000;
