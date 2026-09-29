# Concursos × Candidatos — PostgreSQL de duas formas

Solução do [desafio backend do LEDS](https://github.com/leds-org/venhaparaoleds-backend) em **Java 17 + PostgreSQL**,
implementada duas vezes no mesmo repositório para comparar modelagens:

| Módulo | Ideia | Listas (profissões / vagas) |
|---|---|---|
| [`academico`](academico) | 3ª forma normal | tabelas `profissao`, `candidato_profissao`, `concurso_vaga` (N:N) |
| [`nativo`](nativo) | tipos ricos do PostgreSQL | colunas `text[]` iguais ao CSV + índices **GIN** |
| [`api`](api) | API REST (Spring Boot) | usa qualquer uma das duas, escolhida por configuração |

O módulo [`comum`](comum) tem o que é igual nas duas: leitura/validação dos CSVs, CLI, gerador de dados e a interface
`RepositorioConcursos`. Só a camada de banco muda — então a comparação é justa.

## Funcionalidades

1. **Por CPF do candidato** → concursos compatíveis (órgão, edital, código).
2. **Por código do concurso** → candidatos compatíveis (nome, data de nascimento, CPF).

"Compatível" = o candidato tem **ao menos uma** profissão igual a alguma vaga do concurso.

> O código do concurso **não é único** nos dados (ex.: `19750532858` em SEDURB 16/2019 e DER 4/2017). Por isso ele nunca é
> chave primária; a consulta por código devolve os candidatos de *todos* os concursos com aquele código.

## Como rodar

Requisitos: JDK 17+, Maven, Docker (ou um PostgreSQL 14+ próprio).

```bash
docker compose up -d                 # PostgreSQL com dois bancos: "academico" e "nativo"
mvn package                          # compila, testa e gera academico/target/academico-exe.jar, nativo/target/nativo-exe.jar e api/target/api.jar

# --- solução acadêmica ---
java -jar academico/target/academico-exe.jar importar dados/candidatos.csv dados/concursos.csv
java -jar academico/target/academico-exe.jar concursos-por-cpf 438.150.926-98
java -jar academico/target/academico-exe.jar candidatos-por-concurso 19750532858

# --- solução nativa (mesmos comandos) ---
java -jar nativo/target/nativo-exe.jar importar dados/candidatos.csv dados/concursos.csv
java -jar nativo/target/nativo-exe.jar concursos-por-cpf 438.150.926-98
java -jar nativo/target/nativo-exe.jar candidatos-por-concurso 19750532858
```

Os projetos usam **bancos separados** no mesmo servidor, então podem ser executados em qualquer ordem, quantas vezes
quiser (`importar` é idempotente: recria o esquema se faltar e substitui os dados numa única transação).

Conexão via variáveis de ambiente (padrões casam com o `docker-compose.yml`):
`DB_URL` (padrão `jdbc:postgresql://localhost:5432/<academico|nativo>`), `DB_USER` (`leds`), `DB_PASSWORD` (`leds`).

Os arquivos em [`dados/`](dados) são apenas as 9 primeiras linhas de cada CSV do desafio; passe os arquivos completos no `importar`.
Para testar com volume:

```bash
java -jar nativo/target/nativo-exe.jar gerar 200000 100000 /tmp/gen      # CSVs sintéticos, mesmo formato
java -jar nativo/target/nativo-exe.jar importar /tmp/gen/candidatos.csv /tmp/gen/concursos.csv
```

## API REST

A API expõe as mesmas duas consultas e usa **qualquer** das modelagens, escolhida por variável de ambiente
(as duas ficam no classpath e são descobertas via `ServiceLoader`):

```bash
CONCURSOS_MODELAGEM=academico java -jar api/target/api.jar     # ou nativo; padrão: academico; PORT padrão 8080

curl localhost:8080/api/candidatos/438.150.926-98/concursos    # CPF com ou sem pontuação
curl localhost:8080/api/concursos/19750532858/candidatos
```

| Método e caminho | Resposta 200 |
|---|---|
| `GET /api/candidatos/{cpf}/concursos` | `[{"orgao":"PMES","edital":"6/2022","codigo":53691664314}, ...]` |
| `GET /api/concursos/{codigo}/candidatos` | `[{"nome":"Ana Sophia Araújo","dataNascimento":"2004-12-18","cpf":"438.150.926-98"}, ...]` |

Erros seguem RFC 7807 (`application/problem+json`): **400** para CPF/código inválido (mesmas mensagens da CLI) e
**503** quando o banco falha (o detalhe técnico vai só para o log). Um CPF/código sem correspondência devolve `200` com lista vazia.
A conexão usa as mesmas variáveis da CLI (`DB_URL`, `DB_USER`, `DB_PASSWORD`) através de um pool HikariCP.
As respostas não são paginadas; com bases grandes uma consulta pode devolver dezenas de milhares de itens.

## Arquitetura (módulo `comum`)

```
dominio     records e regras puras (Candidato, Concurso, Cpf)
erro        ErroAplicacao ─┬─ EntradaInvalida       (culpa do usuário/arquivo)
                           └─ ErroInfraestrutura    (banco, recurso ausente)
aplicacao   portas ConsultasConcursos (leitura) e CargaDados (escrita), Modelagem,
            serviços ServicoConsultas e ServicoImportacao (casos de uso)
csv         CsvLeitor, Gerador
jdbc        ConsultasJdbc (execução JDBC; recebe só o SQL), ConfiguracaoBanco, FabricaDataSource
cli         Cli + um Comando por subcomando
```

O módulo `api` é outro adaptador de entrada: `ConsultasController` (HTTP) → `ServicoConsultas` → porta
`ConsultasConcursos`; `TratadorErros` traduz `EntradaInvalida`/`ErroInfraestrutura` em 400/503 e
`ConfiguracaoAplicacao` é sua raiz de composição. Nenhuma classe de `comum` precisou mudar para isso.

* **S** — parser, casos de uso, execução JDBC, formatação da CLI e SQL de cada modelagem são classes distintas.
* **O** — novo subcomando = nova classe `Comando`; nova modelagem = novo módulo com uma `Modelagem`
  registrada em `META-INF/services` (a API/CLI a descobrem via `ServiceLoader`).
* **L** — `ConsultasAcademico` e `ConsultasNativo` são intercambiáveis atrás de `ConsultasConcursos`.
* **I** — quem só consulta (CLI, futura API) depende de `ConsultasConcursos`, sem ver carga de dados.
* **D** — serviços dependem das portas; repositórios recebem um `javax.sql.DataSource`; cada `Main` é a única
  raiz de composição que conhece classes concretas.
* Toda validação de entrada (CPF, código) fica em `ServicoConsultas`, então qualquer porta de entrada
  herda as mesmas regras; `EntradaInvalida` × `ErroInfraestrutura` permite traduzir erros por canal.

## Modelagem

### Acadêmica ([`schema.sql`](academico/src/main/resources/schema.sql))

```
candidato(id PK, nome, data_nascimento, cpf UNIQUE)
concurso(id PK, orgao, edital, codigo)                     + índice (codigo)
profissao(id PK, nome UNIQUE)
candidato_profissao(candidato_id, profissao_id)  PK(candidato_id, profissao_id) + índice (profissao_id, candidato_id)
concurso_vaga(concurso_id, profissao_id)         PK(concurso_id, profissao_id)  + índice (profissao_id, concurso_id)
```

Cada tabela de associação tem **dois índices B-tree**, um para cada sentido de navegação (a PK cobre "de A para B",
o índice invertido cobre "de B para A"). Como incluem as duas colunas, as consultas usam *Index Only Scan*.
Caminho da consulta por CPF: `candidato(cpf)` → `candidato_profissao(PK)` → `concurso_vaga(inv)` → `concurso(PK)`.

### Nativa ([`schema.sql`](nativo/src/main/resources/schema.sql))

```
candidato(cpf PK, nome, data_nascimento, profissoes text[])   + GIN (profissoes)
concurso(id PK, orgao, edital, codigo, vagas text[])          + B-tree (codigo) + GIN (vagas)
```

Não há JOIN por tabela de associação: o operador de sobreposição `&&` (interseção não vazia) faz a compatibilidade e o
GIN o acelera.

```sql
-- concursos de um CPF
SELECT c.orgao, c.edital, c.codigo
  FROM candidato ca JOIN concurso c ON c.vagas && ca.profissoes
 WHERE ca.cpf = ?;
```

Plano observado (200 mil candidatos, 100 mil concursos): `Index Scan candidato_pkey` → `Bitmap Index Scan concurso_vagas_gin`.
A consulta por código é simétrica: `Index Scan concurso_codigo_idx` → `Bitmap Index Scan candidato_profissoes_gin`.

## Comparação (200 mil candidatos / 100 mil concursos, 30 profissões, máquina de desenvolvimento)

| | acadêmico | nativo |
|---|---|---|
| tamanho do banco | 126 MB | 69 MB |
| import | ~20 s | ~7 s |
| `EXPLAIN ANALYZE` por CPF (27 mil linhas) | ~75 ms | ~63 ms |
| `EXPLAIN ANALYZE` por concurso (32 mil linhas) | ~100 ms | ~59 ms |

Resultados idênticos nas duas soluções (mesmas contagens e conteúdo). Observações honestas:

* Com apenas 30 profissões distintas, cada profissão casa com ~10% da base, então o tempo é dominado pelo volume de linhas
  devolvidas, não pela busca. A vantagem do GIN cresce com mais variedade de valores (índice mais seletivo).
* A solução nativa é menor e mais simples de escrever, mas **perde** integridade (nada impede `'pintorr'` — typo) e
  estatísticas por profissão; a acadêmica permite catálogo de profissões, FKs e relatórios por profissão.
* Para ver os planos: `psql -h localhost -U leds -d nativo` e `EXPLAIN (ANALYZE) <consulta>` (em bases minúsculas o
  planejador prefere *Seq Scan*; isso é esperado).

## Tratamento de erros e testes

* Arquivo inexistente, coluna faltando, data/CPF/código inválidos → mensagem com `arquivo:linha`, código de saída 1.
* Importação transacional: se algo falha (ex.: CPF duplicado), nada é gravado.
* Listas são normalizadas (minúsculas, sem espaços extras, sem repetição).
* `mvn test` roda os testes unitários (parser, CPF, serviços e CLI com dublês, sem banco) em `comum` e os da camada HTTP (MockMvc, também sem banco) em `api`.
