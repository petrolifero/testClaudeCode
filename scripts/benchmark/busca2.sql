-- pgbench: busca 2 (candidatos por código) no modelo normalizado, código sorteado entre os gerados
-- por scripts/volume-sintetico.sql.   pgbench -n -t 15 -f scripts/benchmark/busca2.sql <banco>
\set k random(0, 19999)
SELECT ca.nome, ca.data_nascimento, ca.cpf
  FROM candidato ca
 WHERE ca.id IN (SELECT cp.candidato_id FROM candidato_profissao cp
                  WHERE cp.profissao_id IN (SELECT cv.profissao_id
                                              FROM concurso c JOIN concurso_vaga cv ON cv.concurso_id = c.id
                                             WHERE c.codigo = lpad(:k::text, 11, '0')))
 ORDER BY ca.nome, ca.cpf;
