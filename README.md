# Desafio Backend LEDS — versão **acadêmica / normalizada**

> 🤖 **Feito pelo Claude Opus 5.5 (esforço médio)** — branch `opus-5.5-medio/academico-normalizado`.
> Existe uma branch irmã, `opus-5.5-medio/postgres-arrays`, com **a mesma API e os mesmos testes funcionais**,
> mudando só o modelo de dados (arrays nativos + GIN). Assim dá para comparar as duas abordagens lado a lado.

Serviço REST em **Java 21 + Spring Boot 3.5 + PostgreSQL 16** que resolve o
[desafio](https://github.com/leds-org/venhaparaoleds-backend):

1. Dado o **CPF** de um candidato, listar **órgão, código e edital** dos concursos compatíveis.
2. Dado o **código** de um concurso, listar **nome, data de nascimento e CPF** dos candidatos compatíveis.

**Compatível** = ao menos uma profissão do candidato está na lista de vagas do concurso.

---

## Sumário
- [Como executar](#como-executar)
- [API](#api)
- [Modelo de dados (3FN)](#modelo-de-dados-3fn)
- [Consultas e índices](#consultas-e-índices)
- [Desempenho medido (e comparação com a versão com arrays)](#desempenho-medido)
- [Observações sobre os dados](#observações-sobre-os-dados)
- [Arquitetura do código](#arquitetura-do-código)
- [Tratamento de erros](#tratamento-de-erros)
- [Testes](#testes)
- [Diferenciais implementados](#diferenciais-implementados)

## Como executar

### Docker (tudo junto)
```bash
docker compose up --build
# API em http://localhost:8080
```

### Local
Requisitos: JDK 21, Maven 3.9, PostgreSQL 16 (banco `leds`, usuário/senha `postgres`).
```bash
mvn spring-boot:run
# ou: DATABASE_URL=jdbc:postgresql://host:5432/leds DATABASE_USER=... DATABASE_PASSWORD=... mvn spring-boot:run
```

Na primeira subida o **Flyway** cria o esquema e o `ImportadorInicial` carrega
`src/main/resources/dados/{candidatos,concursos}.txt` (os arquivos oficiais do desafio) numa única
transação. Se o banco já tiver dados, a carga é ignorada.

| Variável | Padrão | Para quê |
|---|---|---|
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/leds` | JDBC URL |
| `DATABASE_USER` / `DATABASE_PASSWORD` | `postgres` / `postgres` | credenciais |
| `IMPORTACAO_HABILITADA` | `true` | liga/desliga a carga inicial |
| `ARQUIVO_CANDIDATOS` / `ARQUIVO_CONCURSOS` | `classpath:dados/...` | usar outros arquivos (`file:/caminho`) |

## API

| Método | Rota | Resposta |
|---|---|---|
| `GET` | `/api/candidatos/{cpf}/concursos` | `[{"orgao","codigo","edital"}]` |
| `GET` | `/api/concursos/{codigo}/candidatos` | `[{"nome","dataNascimento","cpf"}]` |
| `GET` | `/actuator/health` | saúde do serviço e do banco |

O CPF pode vir formatado (`438.150.926-98`) ou só com dígitos (`43815092698`).

```bash
curl localhost:8080/api/candidatos/438.150.926-98/concursos
# [{"orgao":"DER","codigo":"13335092204","edital":"8/2023"}, ...]

curl localhost:8080/api/concursos/63912521941/candidatos
# [{"nome":"Agatha Azevedo","dataNascimento":"10/11/1973","cpf":"902.786.153-68"}, ...]
```

## Modelo de dados (3FN)

As colunas "Profissões" e "Lista de vagas" são **atributos multivalorados**: guardá-las numa coluna
violaria a 1ª Forma Normal. Então elas viram relacionamentos N:N com a entidade `profissao`.
O órgão também foi extraído para sua própria tabela.

```
 orgao 1───N concurso N───N profissao N───N candidato
                 └── concurso_vaga ──┘   └── candidato_profissao ──┘
```

| Tabela | Colunas | Chaves / restrições |
|---|---|---|
| `profissao` | `id`, `nome` | PK `id`; `UNIQUE(nome)` |
| `orgao` | `id`, `sigla` | PK `id`; `UNIQUE(sigla)` |
| `candidato` | `id`, `cpf varchar(11)`, `nome`, `data_nascimento date` | PK `id`; `UNIQUE(cpf)`; `CHECK` 11 dígitos |
| `concurso` | `id`, `orgao_id`, `edital`, `codigo varchar(11)` | PK `id`; FK órgão; `CHECK` 11 dígitos |
| `candidato_profissao` | `candidato_id`, `profissao_id` | PK composta; FKs |
| `concurso_vaga` | `concurso_id`, `profissao_id` | PK composta; FKs |

Migração: [`V1__modelo_normalizado.sql`](src/main/resources/db/migration/V1__modelo_normalizado.sql).

## Consultas e índices

| Índice | Serve para |
|---|---|
| `uk_candidato_cpf` (UNIQUE) | achar o candidato da busca 1 |
| `pk_candidato_profissao (candidato_id, profissao_id)` | candidato → profissões |
| `ix_candidato_profissao_inv (profissao_id, candidato_id)` | profissão → candidatos (busca 2) |
| `pk_concurso_vaga (concurso_id, profissao_id)` | concurso → vagas (busca 2) |
| `ix_concurso_vaga_inv (profissao_id, concurso_id)` | profissão → concursos (busca 1) |
| `ix_concurso_codigo` | achar os concursos da busca 2 |
| `ix_concurso_orgao` | índice da FK |

Os índices das tabelas associativas têm **as duas colunas**, nos dois sentidos, de modo que qualquer
caminho pelos relacionamentos é um *Index Only Scan* (não toca o heap).

**Busca 1** (`EXISTS` → semi-join, sem gerar o produto de todas as combinações):
```sql
SELECT DISTINCT o.sigla AS orgao, c.codigo, c.edital
  FROM concurso c JOIN orgao o ON o.id = c.orgao_id
 WHERE EXISTS (SELECT 1 FROM candidato ca
                 JOIN candidato_profissao cp ON cp.candidato_id = ca.id
                 JOIN concurso_vaga cv       ON cv.profissao_id = cp.profissao_id
                WHERE ca.cpf = :cpf AND cv.concurso_id = c.id)
 ORDER BY orgao, c.codigo, c.edital;
```

**Busca 2** (primeiro o conjunto de profissões do código, depois os candidatos; `IN` → semi-joins):
```sql
SELECT ca.nome, ca.data_nascimento, ca.cpf
  FROM candidato ca
 WHERE ca.id IN (SELECT cp.candidato_id FROM candidato_profissao cp
                  WHERE cp.profissao_id IN (SELECT cv.profissao_id
                                              FROM concurso c JOIN concurso_vaga cv ON cv.concurso_id = c.id
                                             WHERE c.codigo = :codigo))
 ORDER BY ca.nome, ca.cpf;
```
A primeira versão da busca 2 usava `EXISTS` com join direto `candidato_profissao ⋈ concurso_vaga`;
como a mesma profissão aparece em vários concursos do mesmo código, isso multiplicava as linhas
intermediárias (735 mil × 3 no volume sintético abaixo). A versão com `IN` aninhado deduplica antes e foi
~12% mais rápida nessa medição.

## Desempenho medido

Os 1000 registros oficiais cabem em poucas páginas: o planejador usa *seq scan* em tudo e índices não
fazem diferença. Para medir de verdade:

- [`scripts/volume-sintetico.sql`](scripts/volume-sintetico.sql) gera **1 000 000 de candidatos**
  (1,89 M pares candidato–profissão) e **100 000 concursos** (230 mil vagas, 5 concursos por código),
  12 profissões — mesma distribuição nas duas branches;
- para a comparação abaixo, os dados do modelo com arrays foram montados **a partir das mesmas linhas**
  do modelo normalizado (mesmo banco, schemas diferentes), então as duas versões respondem exatamente
  as mesmas perguntas sobre exatamente os mesmos dados;
- [`scripts/benchmark/`](scripts/benchmark) tem os roteiros do `pgbench` (CPF/código sorteados a cada
  execução): `pgbench -n -t 300 -f scripts/benchmark/busca1.sql <banco>` (busca 2 com `-t 15`).

PostgreSQL 16, configuração padrão, latência média de 2 rodadas:

| | Normalizado (3FN) | Arrays + GIN |
|---|---|---|
| **Busca 1** — por CPF (~19 mil concursos retornados) | 111 ms | **75–83 ms** |
| **Busca 2** — por código (~850 mil candidatos retornados) | 1,40–1,49 s | **1,28–1,40 s** |
| Espaço em disco (tabelas + índices) | 352 MB | **195 MB** |
| Tabelas / índices | 6 / 11 | 2 / 5 |

O que os números dizem:

1. **Busca 1**: no modelo normalizado são 4 passos (CPF → profissões → `concurso_vaga` → `concurso`,
   com semi-join); com arrays são 2 (CPF → `profissoes`; GIN em `vagas`). O GIN ganhou do *seq scan*
   (75 ms × 101 ms com o bitmap desligado).
2. **Busca 2** é dominada pelo tamanho da resposta: com só 12 profissões, um código com 3–4 profissões
   casa com ~85% dos candidatos, e o tempo vai em materializar e ordenar ~850 mil linhas. Nesse nível de
   seletividade **nenhum índice ajuda de verdade** — forçando *seq scan* na versão com arrays ela cai para
   ~1,0 s, porque o planejador não enxerga o valor vindo da subconsulta (InitPlan) e aposta no GIN.
   Numa API real essa rota pediria paginação; mantive a resposta completa porque é o que o enunciado pede.
3. **Espaço**: cada par candidato–profissão no modelo normalizado é uma tupla (~24 bytes de cabeçalho)
   mais entradas em dois índices B-tree; no array é um elemento dentro da mesma linha, e o GIN comprime
   as *posting lists*.
4. **Armadilha encontrada no caminho (versão normalizada)**: com `char(11)`, comparar com um parâmetro
   `text` faz o PostgreSQL converter a *coluna* (`cpf::text = $1`) e o índice deixa de ser usado
   (111 ms → 463 ms). O driver JDBC manda `varchar` e escapava por sorte; a coluna virou `varchar(11)` e
   o `PlanosDeExecucaoTest` passou a proteger contra regressões desse tipo.

Trade-offs que não aparecem no cronômetro: o modelo normalizado garante integridade referencial das
profissões (não existe "marceneiro" e "marcenero" por erro de digitação sem alguém notar), permite
renomear uma profissão num lugar só e responde facilmente perguntas como "quantos candidatos por
profissão". O modelo com arrays espelha o arquivo, é menor e mais rápido para *estas* duas buscas, e
a validação fica nos `DOMAIN`s — mas renomear uma profissão exige `array_replace` em todas as linhas.

## Observações sobre os dados

- **O "Código do Concurso" não é único**: são 1000 linhas e apenas 199 códigos (o próprio exemplo do
  enunciado mostra SEDU e SEJUS com o mesmo código). Nem `(órgão, edital)` nem `(órgão, edital, código)`
  são únicos — há 2 pares de linhas com os três iguais e vagas diferentes. Por isso:
  - cada linha do arquivo é um `concurso` com chave substituta;
  - a busca 2 considera **todos** os concursos com o código informado (união das vagas);
  - a busca 1 aplica `DISTINCT` em (órgão, código, edital) para não repetir linhas iguais na resposta.
- Todos os 1000 CPFs do arquivo têm dígitos verificadores válidos, então o serviço **valida os DVs**
  (os CPFs de exemplo do enunciado não são válidos — nos testes comportamentais usei os mesmos nomes
  com DVs corrigidos).
- Profissões são normalizadas (espaços extras e caixa) para `"Marceneiro "` casar com `"marceneiro"`.

## Arquitetura do código

```
br.org.leds.concursos
├── dominio/        Cpf, CodigoConcurso, Candidato, Concurso, Profissoes (regras e validações, sem Spring)
├── aplicacao/      BuscaService (casos de uso) + portas BuscaRepository / ImportacaoRepository
├── infra/
│   ├── csv/        LeitorArquivos (Commons CSV), ImportadorInicial (carga no startup)
│   └── persistencia/ PostgresBuscaRepository, PostgresImportacaoRepository (JdbcClient, SQL explícito)
└── web/            BuscaController, TratadorDeErros (Problem Details)
```

- Arquitetura em camadas com **portas e adaptadores**: a aplicação depende de interfaces e só a pasta
  `infra/persistencia` + a migração mudam entre as duas branches.
- **SQL explícito com `JdbcClient`** em vez de JPA: o objetivo do exercício é justamente controlar o
  modelo relacional, as consultas e os índices.
- A carga usa **JDBC batch** (inclusive com retorno das chaves geradas) e é transacional.

## Tratamento de erros

Respostas no padrão **RFC 9457 (Problem Details)**:

| Situação | Status |
|---|---|
| CPF com formato/DV inválido, código sem 11 dígitos | `400` |
| CPF ou código inexistente | `404` |
| Candidato/concurso existe mas nada é compatível | `200` com `[]` |
| Banco indisponível | `503` (detalhes só no log) |

Na importação, qualquer linha inválida (colunas faltando, data inexistente como 31/02, CPF inválido
ou duplicado, lista vazia) aborta a carga com mensagem `arquivo, linha N: motivo`, e nada é gravado.

## Testes

```bash
mvn verify                      # sobe PostgreSQL via Testcontainers (precisa de Docker)
TEST_DATABASE_URL=jdbc:postgresql://localhost:5432/leds_test mvn verify   # usa um PostgreSQL existente
```

| Tipo | Classes | O que cobre |
|---|---|---|
| Unitários | `CpfTest`, `CodigoConcursoTest`, `ProfissoesTest`, `LeitorArquivosTest`, `BuscaServiceTest` | validações, parser, regras de 404 |
| Web | `BuscaControllerTest` (`@WebMvcTest`) | rotas, JSON, 400/404/503 |
| Banco | `PlanosDeExecucaoTest` | as consultas continuam conseguindo usar os índices projetados |
| Integração | `BuscaRepositoryIntegracaoTest` | carrega os arquivos oficiais e compara **as 1000 buscas por CPF e as 199 por código** com um oráculo de força bruta em memória |
| Comportamentais | `busca.feature` (Cucumber, em português) | o exemplo do enunciado, via HTTP real |

Cobertura via JaCoCo em `target/site/jacoco`.

## Diferenciais implementados

| Diferencial | Como |
|---|---|
| Serviço | API REST Spring Boot |
| Banco de dados | PostgreSQL 16 + Flyway |
| Clean Code | camadas, value objects, nomes do domínio, métodos curtos |
| Padrão da tecnologia | convenções Spring Boot (injeção por construtor, `@ConfigurationProperties`, `@RestControllerAdvice`, Problem Details) |
| SonarQube | passo no CI (SonarCloud) + relatório JaCoCo; ativa ao configurar `SONAR_TOKEN` |
| Testes unitários | JUnit 5 + Mockito + AssertJ |
| Testes comportamentais | Cucumber (Gherkin pt-BR) |
| GitHub Actions | `.github/workflows/ci.yml` |
| Docker | `Dockerfile` multi-stage + `docker-compose.yml` |
