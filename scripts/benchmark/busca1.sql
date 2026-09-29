-- pgbench: busca 1 (concursos por CPF) no modelo com arrays, CPF sorteado entre os gerados
-- por scripts/volume-sintetico.sql.   pgbench -n -t 300 -f scripts/benchmark/busca1.sql <banco>
\set id random(1, 1000000)
SELECT DISTINCT orgao, codigo, edital
  FROM concursos
 WHERE vagas && (SELECT profissoes FROM candidatos
                  WHERE cpf = regexp_replace(lpad(:id::text, 11, '0'), '^(\d{3})(\d{3})(\d{3})(\d{2})$', '\1.\2.\3-\4'))
 ORDER BY orgao, codigo, edital;
