# Desafio Backend LEDS — versão **PostgreSQL com arrays + GIN**

> 🤖 **Feito pelo Claude Opus 5.5 (esforço médio)** — branch `opus-5.5-medio/postgres-arrays`.
> Existe uma branch irmã, `opus-5.5-medio/academico-normalizado`, com **a mesma API e os mesmos testes funcionais**,
> mudando só o modelo de dados (3FN com tabelas associativas). Assim dá para comparar as duas abordagens lado a lado.

Serviço REST em **Java 21 + Spring Boot 3.5 + PostgreSQL 16** que resolve o
[desafio](https://github.com/leds-org/venhaparaoleds-backend):

1. Dado o **CPF** de um candidato, listar **órgão, código e edital** dos concursos compatíveis.
2. Dado o **código** de um concurso, listar **nome, data de nascimento e CPF** dos candidatos compatíveis.

**Compatível** = ao menos uma profissão do candidato está na lista de vagas do concurso.

---

## Sumário
- [Como executar](#como-executar)
- [API](#api)
- [Modelo de dados (arrays nativos)](#modelo-de-dados-arrays-nativos)
- [Consultas e índices](#consultas-e-índices)
- [Desempenho medido (e comparação com a versão normalizada)](#desempenho-medido)
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

## Modelo de dados (arrays nativos)

Uma tabela por arquivo, **com as mesmas colunas e a mesma tipagem do CSV**. As listas são `text[]` e
as regras de formato ficam em `DOMAIN`s — o próprio banco rejeita dado inválido, mesmo que alguém
grave direto via `psql`.

```sql
CREATE DOMAIN cpf              AS text   CHECK (VALUE ~ '^[0-9]{3}\.[0-9]{3}\.[0-9]{3}-[0-9]{2}$');
CREATE DOMAIN codigo_concurso  AS text   CHECK (VALUE ~ '^[0-9]{11}$');
CREATE DOMAIN lista_profissoes AS text[] CHECK (cardinality(VALUE) > 0 AND array_position(VALUE, NULL) IS NULL);

CREATE TABLE candidatos (nome text, data_nascimento date, cpf cpf PRIMARY KEY, profissoes lista_profissoes);
CREATE TABLE concursos  (id bigint IDENTITY PRIMARY KEY, orgao text, edital text,
                         codigo codigo_concurso, vagas lista_profissoes);
```
(todas as colunas `NOT NULL`; migração completa em
[`V1__modelo_arrays.sql`](src/main/resources/db/migration/V1__modelo_arrays.sql)).

| Candidatos | | Concursos | |
|---|---|---|---|
| `nome` | `text` | `orgao` | `text` |
| `data_nascimento` | `date` | `edital` | `text` |
| `cpf` | `cpf` (formatado, como no arquivo) | `codigo` | `codigo_concurso` |
| `profissoes` | `lista_profissoes` (`text[]`) | `vagas` | `lista_profissoes` (`text[]`) |

`concursos` tem chave substituta porque nada nos dados é único (ver [observações](#observações-sobre-os-dados)).

## Consultas e índices

"Ter ao menos uma profissão em comum" **é** o operador de sobreposição de arrays `&&`. Um índice
**GIN** indexa cada elemento do array (profissão → lista de linhas), então `&&`, `@>` e `<@` viram
buscas no índice em vez de abrir cada array.

| Índice | Tipo | Serve para |
|---|---|---|
| `pk_candidatos (cpf)` | B-tree | achar as profissões do candidato (busca 1) |
| `ix_concursos_vagas (vagas)` | **GIN** | concursos com vaga para alguma profissão (busca 1) |
| `ix_concursos_codigo (codigo) INCLUDE (vagas)` | B-tree com *covering* | vagas dos concursos de um código, **só pelo índice** (busca 2) |
| `ix_candidatos_profissoes (profissoes)` | **GIN** | candidatos com alguma das profissões (busca 2) |

Além disso, `SET STATISTICS 1000` nas colunas de array melhora as estatísticas por elemento
(`most_common_elems`) que o planejador usa para estimar a seletividade de `&&`.

**Busca 1**
```sql
SELECT DISTINCT orgao, codigo, edital
  FROM concursos
 WHERE vagas && (SELECT profissoes FROM candidatos WHERE cpf = :cpf)
 ORDER BY orgao, codigo, edital;
```

**Busca 2** (vários concursos podem ter o mesmo código: une as vagas de todos)
```sql
SELECT nome, data_nascimento, cpf
  FROM candidatos
 WHERE profissoes && (SELECT array_agg(DISTINCT vaga)
                        FROM concursos c CROSS JOIN LATERAL unnest(c.vagas) AS vaga
                       WHERE c.codigo = :codigo)
 ORDER BY nome, cpf;
```
A subconsulta escalar vira um *InitPlan*: roda uma vez, e o array resultante alimenta o
*Bitmap Index Scan* no GIN. O teste `PlanosDeExecucaoTest` garante que os dois índices continuam
utilizáveis pelas consultas (se alguém mudar um tipo ou envolver a coluna numa função, o teste quebra).

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
- Profissões são normalizadas (espaços extras e caixa) para `"Marceneiro "` casar com `"marceneiro"`,
  deduplicadas e gravadas em ordem alfabética no array.

## Arquitetura do código

```
br.org.leds.concursos
├── dominio/        Cpf, CodigoConcurso, Candidato, Concurso, Profissoes (regras e validações, sem Spring)
├── aplicacao/      BuscaService (casos de uso) + portas BuscaRepository / ImportacaoRepository
├── infra/
│   ├── csv/        LeitorArquivos (Commons CSV), ImportadorInicial (carga no startup)
│   └── persistencia/ PostgresBuscaRepository, PostgresImportacaoRepository (JdbcClient, SQL explícito, text[])
└── web/            BuscaController, TratadorDeErros (Problem Details)
```

- Arquitetura em camadas com **portas e adaptadores**: a aplicação depende de interfaces e só a pasta
  `infra/persistencia` + a migração mudam entre as duas branches.
- **SQL explícito com `JdbcClient`** em vez de JPA: o objetivo do exercício é justamente controlar o
  modelo relacional, as consultas e os índices.
- A carga usa **JDBC batch** com `java.sql.Array` para os `text[]` e é transacional.

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
| Banco | `PlanosDeExecucaoTest`, `RestricoesDoBancoTest` | consultas continuam usando os índices; DOMAINs rejeitam dados inválidos |
| Integração | `BuscaRepositoryIntegracaoTest` | carrega os arquivos oficiais e compara **as 1000 buscas por CPF e as 199 por código** com um oráculo de força bruta em memória |
| Comportamentais | `busca.feature` (Cucumber, em português) | o exemplo do enunciado, via HTTP real |

Cobertura via JaCoCo em `target/site/jacoco`.

## Diferenciais implementados

| Diferencial | Como |
|---|---|
| Serviço | API REST Spring Boot |
| Banco de dados | PostgreSQL 16 + Flyway (DOMAINs, `text[]`, GIN, covering index) |
| Clean Code | camadas, value objects, nomes do domínio, métodos curtos |
| Padrão da tecnologia | convenções Spring Boot (injeção por construtor, `@ConfigurationProperties`, `@RestControllerAdvice`, Problem Details) |
| SonarQube | passo no CI (SonarCloud) + relatório JaCoCo; ativa ao configurar `SONAR_TOKEN` |
| Testes unitários | JUnit 5 + Mockito + AssertJ |
| Testes comportamentais | Cucumber (Gherkin pt-BR) |
| GitHub Actions | `.github/workflows/ci.yml` |
| Docker | `Dockerfile` multi-stage + `docker-compose.yml` |
