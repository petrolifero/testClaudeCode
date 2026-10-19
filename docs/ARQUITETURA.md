# Arquitetura

Recriação da Gupy: um RH publica vagas com critérios de aceite, candidatos se
candidatam enviando currículo em PDF, e cada candidatura passa por uma triagem
automática que aprova ou reprova e avisa o candidato por e-mail.

## Decisões estruturais

| Tema | Decisão | Por quê |
|---|---|---|
| Autenticação | Sem login. Clicar numa persona emite um JWT HS256 | É o enunciado: estando deslogado só existe a página inicial e três pessoas no canto superior direito |
| Transporte do token | Cookie `HttpOnly` + `SameSite=Strict` para o HTML, `Authorization: Bearer` para `/api/**` | O cookie **nunca** autentica `/api/**`; a API fica imune a CSRF por construção e o HTML mantém o token CSRF do Thymeleaf |
| Autorização | Papéis `RH` e `CANDIDATO`, em `SecurityFilterChain` e reforçados no serviço | Defesa em profundidade: a rota e a regra de negócio concordam |
| Persistência | PostgreSQL + Flyway + JPA com `ddl-auto=validate` | O esquema é a fonte da verdade e o Hibernate é obrigado a concordar com ele |
| Filas | Tabelas no próprio banco (padrão outbox) com lease | Não perder e não duplicar são garantias de banco, não de código |
| Triagem | Interface `AnalisadorDeCandidatura`, duas implementações | Trocar busca textual por LLM (ou por vetores) é mudar uma propriedade |
| Extração de PDF | Interface `ExtratorDeTextoDePdf`, camada de texto com fallback para OCR | O enunciado pede os dois tipos de PDF |
| OCR | Binário `tesseract` por linha de comando, atrás de `MotorDeOcr` | O Tess4J empacota um `lept4j` que exige Leptonica mais nova que a do Ubuntu Noble; chamar o binário não tem acoplamento de ABI |
| Testes | Integração com PostgreSQL real (Testcontainers), **sem nenhum mock** | Mockito é excluído no `pom.xml` de propósito |

## Como "não perder nenhuma e não duplicar" é garantido

Três camadas, cada uma suficiente contra um modo de falha diferente:

1. **Não duplicar inscrição** — `unique (vaga_id, candidato_id)` em `candidatura`.
2. **Não perder análise** — `analise_job` é gravado na **mesma transação** da
   candidatura e tem `unique (candidatura_id)`. Se a candidatura existe, o job
   existe, e existe exatamente um.
3. **Não processar duas vezes** — o consumo usa lease:
   `UPDATE ... WHERE id IN (SELECT ... FOR UPDATE SKIP LOCKED) RETURNING id`
   entrega cada linha a um único worker. Se o worker morre, o lease expira e o
   job volta para `PENDENTE`.
4. **Exatamente uma conclusão** — concluir é
   `UPDATE ... WHERE id = ? AND status = 'RECEBIDA'`, e a porta
   `Candidaturas.concluir(...)` devolve verdadeiro para quem chegou primeiro e
   falso para os demais. **Só quem recebeu verdadeiro enfileira o e-mail.**
5. **Não duplicar e-mail** — `unique (candidatura_id, tipo)` em `email_outbox`,
   gravado na mesma transação da conclusão.

6. Esgotadas as tentativas o item vai para `FALHO`, e a **quarentena**
   (`gupy.triagem.quarentena` / `gupy.email.quarentena`) o devolve à fila
   depois de um tempo, com as tentativas zeradas. `FALHO` é descanso, não
   túmulo: sem isso, uma indisponibilidade de segundos da análise deixava a
   candidatura sem decisão para sempre.
7. O atraso entre tentativas é **exponencial**: com atraso fixo, três
   tentativas se esgotavam em dez segundos.

Reprocessar é portanto inofensivo: o resultado é sempre uma transição de estado
e um e-mail. O envio SMTP em si é *at-least-once* (um processo que morra entre o
`send` e o commit reenvia) — isso está documentado e é o limite honesto de
entrega por SMTP.

## Mapa de pacotes

```
com.gupyclone
├── comum/           EntidadeComId, ErroDeNegocio, TratadorDeErrosDaApi
├── config/          Propriedades*, JwtConfig, SegurancaConfig
├── seguranca/       Papel, UsuarioAutenticado, ServicoDeToken, filtro JWT,
│                    ServicoDePersonas, entrar/sair (HTML e API)
├── pessoas/         Candidato, Recrutador, porta ConsultaDePessoas
├── vagas/           Vaga (recrutador por id, critérios por objeto),
│                    CriterioAceite, StatusVaga, porta ConsultaDeVagas,
│                    MontadorDeVagaResposta
├── curriculos/      Curriculo, OrigemTexto, TextoExtraido,
│                    ExtratorDeTextoDePdf, MotorDeOcr, RecebedorDeCurriculos
├── candidaturas/    Candidatura (vaga, candidato, currículo e recrutador por
│                    id), portas Candidaturas e EnfileiramentoDeTriagem
├── triagem/         AnalisadorDeCandidatura, ContextoDaAnalise,
│                    ResultadoDaAnalise, AnaliseJob, ReivindicadorDeAnalises
├── notificacoes/    EmailOutbox, FilaDeEmails, RedatorDeEmail,
│                    ReivindicadorDeEmails
└── web/             páginas Thymeleaf
```

## Contrato HTTP

### Sessão (sem autenticação prévia)

| Método | Rota | Papel | Resposta |
|---|---|---|---|
| `GET` | `/api/auth/personas` | público | `[{chave, nome, papel}]` |
| `POST` | `/api/auth/entrar/{chave}` | público | `{token, chave, nome, papel, entidadeId}`; **409** se já autenticado (por `Bearer` **ou** por cookie). Não emite cookie: cookie é do fluxo HTML |
| `POST` | `/api/auth/sair` | público | 204 e cookie expirado |
| `GET` | `/api/eu` | autenticado | `{chave, nome, papel, entidadeId}`; 401 se anônimo |

### Vagas

| Método | Rota | Papel | Corpo / resposta |
|---|---|---|---|
| `POST` | `/api/vagas` | `RH` | `{titulo, descricao, pontuacaoMinima, criterios:[{descricao, expressao, peso, obrigatorio}]}` → 201 `VagaResposta` |
| `GET` | `/api/vagas` | autenticado | `[VagaResposta]` (apenas abertas) |
| `GET` | `/api/vagas/{id}` | autenticado | `VagaResposta`; 404 se não existir |

`VagaResposta`: `{id, titulo, descricao, status, pontuacaoMinima, recrutador:{id,nome}, criterios:[{id, descricao, expressao, peso, obrigatorio}], criadoEm}`

`expressao` vem **`null`** para quem não é o RH dono da vaga: ela é o critério
de triagem em forma executável e diria ao candidato o que escrever para passar.

### Candidaturas

| Método | Rota | Papel | Corpo / resposta |
|---|---|---|---|
| `POST` | `/api/vagas/{id}/candidaturas` | `CANDIDATO` | multipart com a parte `arquivo` (PDF) → 201 `CandidaturaResposta`; **409** se já candidatado; **409** se a vaga estiver fechada; **422** se o PDF for ilegível |
| `GET` | `/api/candidaturas/minhas` | `CANDIDATO` | `[CandidaturaResposta]` |
| `GET` | `/api/vagas/{id}/candidaturas` | `RH` | `[CandidaturaResposta]`; **403** se a vaga não for do RH autenticado |

`CandidaturaResposta`: `{id, vagaId, tituloDaVaga, candidatoId, nomeDoCandidato, status, pontuacao, motivo, origemDoTexto, criadoEm, analisadaEm}`

### Páginas

| Rota | Papel | Conteúdo |
|---|---|---|
| `GET /` | público | Página inicial. Deslogado: as três pessoas no canto superior direito. Logado: nome, papel e **Sair** no mesmo canto, e **nenhuma** opção de entrar como outro |
| `POST /auth/entrar/{chave}` | público | Entra e redireciona para `/vagas` |
| `POST /auth/sair` | público | Sai e redireciona para `/` |
| `GET /vagas` | autenticado | Lista de vagas abertas |
| `GET /vagas/{id}` | autenticado | Detalhe; candidato vê o formulário de envio de currículo |
| `POST /vagas/{id}/candidatar` | `CANDIDATO` | Multipart `arquivo` |
| `GET /minhas-candidaturas` | `CANDIDATO` | Status de cada candidatura |
| `GET /rh/vagas` | `RH` | Vagas do RH e candidaturas de cada uma |
| `GET /rh/vagas/nova`, `POST /rh/vagas` | `RH` | Formulário de nova vaga com critérios |

## Convenções

- **Português** em pacotes, classes, métodos, colunas e mensagens. Siglas
  consagradas (`id`, `jwt`, `ocr`, `pdf`, `api`, `rh`) ficam como são.
- Sem acentos em identificadores Java e em literais SQL de migração.
- Acentuação em mensagens é decidida por quem lê:
  - **com acento** tudo que chega a uma pessoa: HTML, corpo e assunto de
    e-mail, mensagens de `ErroDeNegocio` e de Bean Validation (viram corpo de
    resposta da API);
  - **sem acento** mensagens que só aparecem em log ou em exceção de violação
    de contrato de programação (`IllegalArgumentException`,
    `IllegalStateException`), que nunca chegam ao candidato.
- Toda exceção de domínio estende `comum.ErroDeNegocio` e declara seu status
  HTTP; quem traduz para JSON é `comum.TratadorDeErrosDaApi`.
- Entidades estendem `comum.EntidadeComId` (id, `equals`/`hashCode`).
- Serviços anotados com `@Transactional`; leitura com `readOnly = true`.
- Comentário só onde explica *por quê*, nunca repetindo o que o código diz.

## Agendamento

Os `@Scheduled` que varrem as filas ficam em classes `Agendador*` separadas dos
processadores, anotadas com
`@ConditionalOnProperty(name = "gupy.agendamento.ativo", havingValue = "true", matchIfMissing = true)`.
Os testes rodam com `gupy.agendamento.ativo: false` e chamam
`processarUmLote()` / `despacharUmLote()` diretamente: o teste decide quando o
trabalho acontece, e nenhuma asserção disputa com o agendador. Um teste
dedicado liga a propriedade para provar que a varredura automática funciona de
verdade.

## Testes

- Todos herdam de `suporte.TesteDeIntegracao`: PostgreSQL real em container
  (um por JVM), migrações de verdade, HTTP em porta real via `RestTestClient`.
- `clienteComo("ana" | "carlos" | "joao")` devolve um cliente autenticado;
  `clienteAnonimo()` é o visitante deslogado.
- `suporte.FabricaDePdf` gera PDFs reais: `comCamadaDeTexto(...)`,
  `somenteImagem(...)` (exige OCR) e `paginaEmBranco()`.
- E-mail é verificado com GreenMail, um servidor SMTP real na porta 3025.
- Nada de mocks: `mockito-core` e `mockito-junit-jupiter` são excluídos do
  `spring-boot-starter-test` no `pom.xml`.
- Para testar caminho de falha, prefira **provocar a falha de verdade** (parar
  o GreenMail, apontar o cliente HTTP para uma porta onde ninguém escuta) em
  vez de criar um dublê. Quando não houver alternativa, um *fake* escrito à mão
  num `@TestConfiguration` é aceitável; biblioteca de mock, não.

## Rodando

```bash
# dependências de sistema
apt-get install -y tesseract-ocr tesseract-ocr-por

# testes (sobe PostgreSQL via Testcontainers; precisa de Docker)
mvn test

# aplicação (precisa de um PostgreSQL e de um SMTP; veja docker-compose.yml)
docker compose up -d
mvn spring-boot:run
```
