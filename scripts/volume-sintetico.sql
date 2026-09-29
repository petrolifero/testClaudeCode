-- =====================================================================
-- Gera volume sintético no modelo normalizado para avaliar os planos de
-- execução (EXPLAIN) das duas buscas.  NÃO usar em produção.
--
--   psql -v candidatos=1000000 -v concursos=100000 -f scripts/volume-sintetico.sql <banco>
--
-- Os CPFs gerados não têm dígitos verificadores válidos; use os CPFs
-- impressos no final (ou um dos arquivos oficiais) para testar.
-- =====================================================================
\set ON_ERROR_STOP on
\if :{?candidatos} \else \set candidatos 1000000 \endif
\if :{?concursos}  \else \set concursos 100000   \endif

BEGIN;
TRUNCATE candidato_profissao, concurso_vaga, candidato, concurso, profissao, orgao RESTART IDENTITY CASCADE;

INSERT INTO profissao (nome)
SELECT unnest(ARRAY['analista de sistemas', 'arquiteto', 'assistente administrativo', 'carpinteiro',
                    'eletricista', 'encanador', 'engenheiro civil', 'marceneiro', 'pedreiro', 'pintor',
                    'professor de matemática', 'técnico em segurança do trabalho']);

INSERT INTO orgao (sigla)
SELECT unnest(ARRAY['PCES', 'PMES', 'SEAG', 'SECONT', 'SEDU', 'SEDURB', 'SEFAZ', 'SEJUS', 'SESA']);

INSERT INTO candidato (cpf, nome, data_nascimento)
SELECT lpad(g::text, 11, '0'), 'Candidato ' || g, date '1950-01-01' + (random() * 20000)::int
  FROM generate_series(1, :candidatos) g;

-- 1 a 3 profissões por candidato
INSERT INTO candidato_profissao (candidato_id, profissao_id)
SELECT DISTINCT c.id, 1 + floor(random() * 12)::int
  FROM candidato c, generate_series(1, 1 + (c.id % 3)::int);

INSERT INTO concurso (orgao_id, edital, codigo)
SELECT 1 + (g % 9), (1 + g % 20) || '/' || (2015 + g % 8), lpad((g % (:concursos / 5))::text, 11, '0')
  FROM generate_series(1, :concursos) g;

-- 1 a 4 vagas por concurso
INSERT INTO concurso_vaga (concurso_id, profissao_id)
SELECT DISTINCT c.id, 1 + floor(random() * 12)::int
  FROM concurso c, generate_series(1, 1 + (c.id % 4)::int);
COMMIT;

VACUUM ANALYZE;
