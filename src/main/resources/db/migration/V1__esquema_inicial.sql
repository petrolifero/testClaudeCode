-- Esquema inicial do clone da Gupy.
--
-- Decisoes importantes registradas aqui porque o banco e a fonte da verdade
-- das garantias de "nao perder nenhuma candidatura e nao duplicar":
--   * candidatura tem unique (vaga_id, candidato_id)  -> nao duplica inscricao
--   * analise_job   tem unique (candidatura_id)       -> nao duplica analise
--   * email_outbox  tem unique (candidatura_id, tipo) -> nao duplica e-mail
-- As filas sao tabelas, escritas na mesma transacao do fato que as origina
-- (padrao outbox), e consumidas com SELECT ... FOR UPDATE SKIP LOCKED.

create extension if not exists unaccent;

-- Configuracao de full text search em portugues insensivel a acentos.
-- to_tsvector(regconfig, text) e immutable, portanto pode ser usada em
-- coluna gerada e em indice.
do $$
begin
    if not exists (select 1 from pg_ts_config where cfgname = 'portugues_sem_acento') then
        create text search configuration portugues_sem_acento ( copy = portuguese );
        alter text search configuration portugues_sem_acento
            alter mapping for hword, hword_part, word with unaccent, portuguese_stem;
    end if;
end
$$;

create table candidato (
    id        bigserial    primary key,
    chave     varchar(40)  not null unique,
    nome      varchar(120) not null,
    email     varchar(180) not null unique,
    criado_em timestamptz  not null default now()
);

create table recrutador (
    id        bigserial    primary key,
    chave     varchar(40)  not null unique,
    nome      varchar(120) not null,
    email     varchar(180) not null unique,
    criado_em timestamptz  not null default now()
);

create table vaga (
    id               bigserial    primary key,
    titulo           varchar(160) not null,
    descricao        text         not null,
    recrutador_id    bigint       not null references recrutador (id),
    status           varchar(20)  not null,
    pontuacao_minima int          not null default 0,
    criado_em        timestamptz  not null default now(),
    constraint vaga_status_valido check (status in ('ABERTA', 'FECHADA')),
    constraint vaga_pontuacao_minima_valida check (pontuacao_minima between 0 and 100)
);
create index vaga_recrutador_idx on vaga (recrutador_id);
create index vaga_status_idx on vaga (status);

-- Criterio de aceite definido pelo RH. "expressao" alimenta o analisador
-- textual (websearch_to_tsquery aceita "java or jvm", "-php", frases entre
-- aspas e nunca estoura erro de sintaxe com entrada livre).
create table criterio_aceite (
    id          bigserial    primary key,
    vaga_id     bigint       not null references vaga (id) on delete cascade,
    descricao   varchar(200) not null,
    expressao   varchar(200) not null,
    peso        int          not null default 1,
    obrigatorio boolean      not null default false,
    constraint criterio_peso_valido check (peso between 1 and 100)
);
create index criterio_aceite_vaga_idx on criterio_aceite (vaga_id);

create table curriculo (
    id           bigserial    primary key,
    candidato_id bigint       not null references candidato (id) on delete cascade,
    nome_arquivo varchar(255) not null,
    conteudo_pdf bytea        not null,
    texto        text         not null,
    origem_texto varchar(20)  not null,
    paginas      int          not null,
    criado_em    timestamptz  not null default now(),
    texto_busca  tsvector     generated always as (to_tsvector('portugues_sem_acento', texto)) stored,
    constraint curriculo_origem_valida check (origem_texto in ('CAMADA_DE_TEXTO', 'OCR')),
    constraint curriculo_paginas_valido check (paginas >= 0)
);
create index curriculo_candidato_idx on curriculo (candidato_id);
create index curriculo_texto_busca_idx on curriculo using gin (texto_busca);

create table candidatura (
    id            bigserial   primary key,
    vaga_id       bigint      not null references vaga (id),
    candidato_id  bigint      not null references candidato (id),
    curriculo_id  bigint      not null references curriculo (id),
    recrutador_id bigint      not null references recrutador (id),
    status        varchar(20) not null,
    pontuacao     int,
    motivo        text,
    criado_em     timestamptz not null default now(),
    analisada_em  timestamptz,
    constraint candidatura_unica_por_vaga unique (vaga_id, candidato_id),
    constraint candidatura_status_valido check (status in ('RECEBIDA', 'APROVADA', 'REPROVADA')),
    constraint candidatura_pontuacao_valida check (pontuacao is null or pontuacao between 0 and 100)
);
create index candidatura_vaga_idx on candidatura (vaga_id);
create index candidatura_candidato_idx on candidatura (candidato_id);

-- Fila de analise. Uma linha por candidatura (unique), criada na mesma
-- transacao da candidatura: se a candidatura existe, o job existe.
--
-- O consumo usa lease (arrendamento) em duas transacoes curtas, para nao
-- manter transacao aberta durante a analise:
--   1. reivindicar: UPDATE ... WHERE id IN (SELECT ... FOR UPDATE SKIP LOCKED)
--      marca PROCESSANDO e devolve os ids via RETURNING
--   2. concluir:    grava o resultado e marca CONCLUIDO
-- Se o worker morrer no meio, o lease expira e o job volta para PENDENTE
-- (nada se perde). Reprocessar e inofensivo porque a transicao da candidatura
-- e idempotente e o e-mail tem unique (candidatura_id, tipo) (nada duplica).
create table analise_job (
    id              bigserial   primary key,
    candidatura_id  bigint      not null unique references candidatura (id) on delete cascade,
    status          varchar(20) not null,
    tentativas      int         not null default 0,
    ultimo_erro     text,
    disponivel_em   timestamptz not null default now(),
    reivindicado_em timestamptz,
    criado_em       timestamptz not null default now(),
    atualizado_em   timestamptz not null default now(),
    constraint analise_job_status_valido
        check (status in ('PENDENTE', 'PROCESSANDO', 'CONCLUIDO', 'FALHO'))
);
create index analise_job_fila_idx on analise_job (status, disponivel_em);
create index analise_job_lease_idx on analise_job (status, reivindicado_em);

-- Fila de e-mails. Uma linha por (candidatura, tipo), criada na mesma
-- transacao em que a candidatura recebe seu status final.
create table email_outbox (
    id              bigserial    primary key,
    candidatura_id  bigint       not null references candidatura (id) on delete cascade,
    tipo            varchar(30)  not null,
    destinatario    varchar(180) not null,
    assunto         varchar(255) not null,
    corpo           text         not null,
    status          varchar(20)  not null,
    tentativas      int          not null default 0,
    ultimo_erro     text,
    disponivel_em   timestamptz  not null default now(),
    reivindicado_em timestamptz,
    criado_em       timestamptz  not null default now(),
    enviado_em      timestamptz,
    constraint email_unico_por_candidatura unique (candidatura_id, tipo),
    constraint email_tipo_valido check (tipo in ('CANDIDATURA_APROVADA', 'CANDIDATURA_REPROVADA')),
    constraint email_status_valido
        check (status in ('PENDENTE', 'ENVIANDO', 'ENVIADO', 'FALHO'))
);
create index email_outbox_fila_idx on email_outbox (status, disponivel_em);
create index email_outbox_lease_idx on email_outbox (status, reivindicado_em);
