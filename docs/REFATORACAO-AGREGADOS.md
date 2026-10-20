# Refatoração: agregados referenciados por identidade

Contrato congelado da mudança. Nasceu de uma revisão humana que seguiu uma
trilha certeira: o `CandidaturaRepository` parecia inchado, o que levou a um
`Hibernate.initialize` no serviço, que levou à pergunta de por que ele é
necessário — e a resposta é que o modelo trata quatro agregados como um só.

## O defeito, em uma frase

`Candidatura` referencia `Vaga`, `Candidato`, `Curriculo` e `Recrutador` como
**entidades** (`@ManyToOne`). Três consequências medidas:

1. `ServicoDeCandidaturas` precisa de **5 repositórios** só para montar uma
   candidatura (todos os outros serviços usam 1 ou 2).
2. Como `spring.jpa.open-in-view` é `false`, as associações chegam destacadas, e
   o código contorna isso com `Hibernate.initialize` — um sintoma, não um
   desenho.
3. Para ler a candidatura, `triagem` e `notificacoes` alcançam direto o
   `CandidaturaRepository` e a entidade `Candidatura`, e `candidaturas` alcança
   `AnaliseJob` — **um ciclo entre pacotes**.

O esquema do banco **já está certo**: `candidatura` tem `vaga_id`,
`candidato_id`, `curriculo_id` e `recrutador_id`. Não há migração nesta
refatoração. O erro está só no mapeamento.

## Regra que passa a valer

> Dentro de um agregado, referência por objeto. **Entre** agregados, referência
> por id — e travessia só por porta do módulo dono.

Agregados: `Vaga` (com seus `CriterioAceite`), `Candidato`, `Recrutador`,
`Curriculo`, `Candidatura`, `AnaliseJob`, `EmailOutbox`.
`CriterioAceite` continua `@OneToMany` dentro de `Vaga`: é parte do agregado.

## Portas de leitura (records de dados, sem JPA)

Cada módulo dono expõe a sua. Implementação no próprio módulo.

```java
// com.gupyclone.vagas
public record DadosDaVaga(Long id, String titulo, String descricao,
                          StatusVaga status, int pontuacaoMinima, Long recrutadorId) {
    public boolean estaAberta() { return status == StatusVaga.ABERTA; }
}
public interface ConsultaDeVagas { Optional<DadosDaVaga> porId(Long id); }

// com.gupyclone.pessoas
public record DadosDoCandidato(Long id, String chave, String nome, String email) {}
public record DadosDoRecrutador(Long id, String chave, String nome, String email) {}
public interface ConsultaDePessoas {
    Optional<DadosDoCandidato> candidato(Long id);
    Optional<DadosDoRecrutador> recrutador(Long id);
}

// com.gupyclone.curriculos
public record DadosDoCurriculo(Long id, Long candidatoId, String nomeArquivo,
                               OrigemTexto origemTexto, int paginas) {}
public interface ConsultaDeCurriculos { Optional<DadosDoCurriculo> porId(Long id); }
```

## Portas de candidaturas

```java
// com.gupyclone.candidaturas

// ENTRADA: quem precisa ler ou concluir uma candidatura usa isto, nunca o repositório.
public record DadosParaAnalise(Long candidaturaId, Long vagaId, Long candidatoId,
                               Long curriculoId, Long recrutadorId, StatusCandidatura status) {}
public interface Candidaturas {
    Optional<DadosParaAnalise> paraAnalise(Long candidaturaId);
    /** @return true somente para quem efetivou a transição (RECEBIDA -> final) */
    boolean concluir(Long id, StatusCandidatura status, int pontuacao, String motivo);
}

// SAÍDA: é o que quebra o ciclo. Declarada aqui, IMPLEMENTADA em triagem.
public interface EnfileiramentoDeTriagem {
    /** Chamado DENTRO da transação que grava a candidatura (padrão outbox). */
    void enfileirar(Long candidaturaId);
}
```

Com isso `candidaturas` passa a importar **zero** de `triagem`.

## Entidades que mudam

```java
// Candidatura: ids, não entidades. Mesmas colunas, mesmo esquema.
@Column(name = "vaga_id",       nullable = false, updatable = false) private Long vagaId;
@Column(name = "candidato_id",  nullable = false, updatable = false) private Long candidatoId;
@Column(name = "curriculo_id",  nullable = false, updatable = false) private Long curriculoId;
@Column(name = "recrutador_id", nullable = false, updatable = false) private Long recrutadorId;
public Candidatura(Long vagaId, Long candidatoId, Long curriculoId, Long recrutadorId)

// AnaliseJob: @OneToOne Candidatura  ->  Long candidaturaId (coluna candidatura_id, unique)
// EmailOutbox: @ManyToOne Candidatura -> Long candidaturaId
// Vaga: @ManyToOne Recrutador -> Long recrutadorId
```

## Notificações deixam de conhecer Candidatura

```java
// com.gupyclone.notificacoes
public record AvisoAoCandidato(Long candidaturaId, TipoEmail tipo, String destinatario,
                               String nomeDoCandidato, String tituloDaVaga,
                               int pontuacao, String motivo) {}
public interface FilaDeEmails { void enfileirar(AvisoAoCandidato aviso); }
public interface RedatorDeEmail { EmailOutbox redigir(AvisoAoCandidato aviso); }
```

Quem monta o `AvisoAoCandidato` é a triagem, que já tem os dados em mão. Com
isso `notificacoes` importa **zero** de `candidaturas`.

`FilaDeEmails.enfileirar` continua `@Transactional(MANDATORY)`: a garantia de
outbox não muda.

## Hibernate.initialize: zero ocorrências em `src/main`

- Em `candidaturas`: desaparece, porque não há associação para inicializar.
- `CandidaturaResposta.de(Candidatura)` **morre**. A resposta passa a vir só de
  projeção; o repositório ganha `resumoPorId(Long)` para o caminho de uma
  candidatura só.
- Em `vagas`: `VagaRepository` ganha consultas com `left join fetch v.criterios`
  para `porId`, `abertas` e `doRecrutador`. Isso também mata o N+1 que a fatia
  de vagas havia declarado como pendência, e o truque `getCriterios().size()`.
- `VagaResposta` obtém o nome do recrutador por `ConsultaDePessoas`.

## O que NÃO muda

- O esquema do banco. Nenhuma migração.
- As garantias de não-perder/não-duplicar: os `unique`, o outbox na mesma
  transação, o lease com `SKIP LOCKED`, a conclusão idempotente e a quarentena
  seguem iguais. Os testes de concorrência devem continuar passando **sem
  afrouxar nenhuma asserção**.
- O contrato HTTP. Nenhum corpo de resposta muda de forma.
- A regra de acento, nomes em português e comentário só para explicar porquê.

## Como saber que deu certo

Além da suíte verde nas duas ordens (183 testes), os números abaixo foram
**contados** com `grep` sobre `src/main/java`, no commit anterior à refatoração
(`0aa7c30`) e agora:

| medida | antes | alvo | medido agora |
|---|---|---|---|
| `Hibernate.initialize` em `src/main` | 4 | **0** | **0** |
| repositórios injetados em `ServicoDeCandidaturas` | 5 | **1** | **1** |
| `candidaturas` importa de `triagem` | 2 | **0** | **0** |
| `notificacoes` importa de `candidaturas` | 5 | **0** | **0** |
| arquivos usando repositório de outro pacote | 5 | **0** | **1** |
| entidade de outro agregado por `@ManyToOne`/`@OneToOne` | 8 | **0** | **0** |
| ciclos entre pacotes de domínio | 2 | **0** | **0** |

Três observações, porque número sem método é estimativa:

1. **`Hibernate.initialize`: 4 chamadas, mas 5 ocorrências de texto.** A quinta
   era prosa num comentário. Como a medida é textual — e tem de ser, senão a
   história do defeito reintroduz a palavra sem reintroduzir o defeito — a fase
   que zerou a conta precisou reescrever aquele comentário. Hoje
   `grep -rn Hibernate src/main` devolve 3 linhas, todas sobre o ORM e nenhuma
   com `.initialize`.
2. **Sobrou 1 repositório fora do pacote**, e não 0:
   `seguranca/ServicoDePersonas` lê `CandidatoRepository` e
   `RecrutadorRepository`. Ele resolve persona *por chave* e lista todas, e a
   porta `ConsultaDePessoas` — congelada acima — só lê por id. Fechar isso é
   ampliar a porta, o que muda este contrato; está registrado como dívida
   explícita no teste de arquitetura, arquivo por arquivo, de modo que uma
   violação **nova** continua falhando e quem consertar esta é obrigado a apagar
   a entrada.
3. **As 8 associações entre agregados incluem uma que o contrato não previa.**
   `Curriculo` referenciava `Candidato` por `@ManyToOne`, e a lista de
   "Entidades que mudam" não a citava. Quem a encontrou foi o próprio teste de
   arquitetura ao ser escrito: a regra 4 falhava no primeiro `mvn test`. Virou
   `Long candidatoId`, no mesmo padrão de `Vaga.recrutadorId`, e com isso a
   regra vale **sem exceção**.

## O teste que impede o retorno

`src/test/java/com/gupyclone/arquitetura/RegrasDeArquiteturaTest.java`, 7
testes, sem container e sem biblioteca nova: lê os `.java` de `src/main/java`
com `java.nio.file` e afirma sobre o texto. Afirma, nesta ordem: o inventário
lido (nenhuma regra pode passar por ler arquivo nenhum), ausência de
`Hibernate.initialize`, ausência de ciclo no grafo de domínio, repositório
preso ao seu pacote, ausência de `@ManyToOne`/`@OneToOne` entre pacotes de
domínio, e as duas medidas conquistadas (`notificacoes` ⇏ `candidaturas`,
`candidaturas` ⇏ `triagem`).

Cada regra foi **falsificada** antes de ser aceita: a violação correspondente
foi reintroduzida em `src/main`, a regra falhou, e a violação foi desfeita. Uma
regra que não falha quando deveria dá falsa confiança, que é pior que não ter
regra.
