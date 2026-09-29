-- pgbench: busca 2 (candidatos por código) no modelo com arrays, código sorteado entre os gerados
-- por scripts/volume-sintetico.sql.   pgbench -n -t 15 -f scripts/benchmark/busca2.sql <banco>
\set k random(0, 19999)
SELECT nome, data_nascimento, cpf
  FROM candidatos
 WHERE profissoes && (SELECT array_agg(DISTINCT vaga)
                        FROM concursos c CROSS JOIN LATERAL unnest(c.vagas) AS vaga
                       WHERE c.codigo = lpad(:k::text, 11, '0'))
 ORDER BY nome, cpf;
