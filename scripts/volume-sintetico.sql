-- =====================================================================
-- Gera volume sintético no modelo com arrays para avaliar os planos de
-- execução (EXPLAIN) das duas buscas.  NÃO usar em produção.
--
--   psql -v candidatos=1000000 -v concursos=100000 -f scripts/volume-sintetico.sql <banco>
--
-- Mesma distribuição da branch normalizada: 12 profissões, 1 a 3 por
-- candidato, 1 a 4 vagas por concurso, 5 concursos por código.
-- Os CPFs gerados não têm dígitos verificadores válidos.
-- =====================================================================
\set ON_ERROR_STOP on
\if :{?candidatos} \else \set candidatos 1000000 \endif
\if :{?concursos}  \else \set concursos 100000   \endif

BEGIN;
TRUNCATE candidatos, concursos RESTART IDENTITY;

-- n profissões sorteadas (com reposição, depois deduplicadas e ordenadas)
CREATE FUNCTION pg_temp.sortear_profissoes(n int) RETURNS text[] LANGUAGE sql VOLATILE AS $$
    SELECT array_agg(DISTINCT p ORDER BY p)
      FROM (SELECT (ARRAY['analista de sistemas', 'arquiteto', 'assistente administrativo', 'carpinteiro',
                          'eletricista', 'encanador', 'engenheiro civil', 'marceneiro', 'pedreiro', 'pintor',
                          'professor de matemática', 'técnico em segurança do trabalho'])[1 + floor(random() * 12)::int] AS p
              FROM generate_series(1, n)) s
$$;

INSERT INTO candidatos (nome, data_nascimento, cpf, profissoes)
SELECT 'Candidato ' || g,
       date '1950-01-01' + (random() * 20000)::int,
       regexp_replace(lpad(g::text, 11, '0'), '^(\d{3})(\d{3})(\d{3})(\d{2})$', '\1.\2.\3-\4'),
       pg_temp.sortear_profissoes(1 + (g % 3))
  FROM generate_series(1, :candidatos) g;

INSERT INTO concursos (orgao, edital, codigo, vagas)
SELECT (ARRAY['PCES', 'PMES', 'SEAG', 'SECONT', 'SEDU', 'SEDURB', 'SEFAZ', 'SEJUS', 'SESA'])[1 + g % 9],
       (1 + g % 20) || '/' || (2015 + g % 8),
       lpad((g % (:concursos / 5))::text, 11, '0'),
       pg_temp.sortear_profissoes(1 + (g % 4))
  FROM generate_series(1, :concursos) g;
COMMIT;

VACUUM ANALYZE;
