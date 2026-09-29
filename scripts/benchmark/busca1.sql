-- pgbench: busca 1 (concursos por CPF) no modelo normalizado, CPF sorteado entre os gerados
-- por scripts/volume-sintetico.sql.   pgbench -n -t 300 -f scripts/benchmark/busca1.sql <banco>
\set id random(1, 1000000)
SELECT DISTINCT o.sigla AS orgao, c.codigo, c.edital
  FROM concurso c JOIN orgao o ON o.id = c.orgao_id
 WHERE EXISTS (SELECT 1 FROM candidato ca
                 JOIN candidato_profissao cp ON cp.candidato_id = ca.id
                 JOIN concurso_vaga cv       ON cv.profissao_id = cp.profissao_id
                WHERE ca.cpf = lpad(:id::text, 11, '0') AND cv.concurso_id = c.id)
 ORDER BY orgao, c.codigo, c.edital;
