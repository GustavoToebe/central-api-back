-- Domínio comercial da Central (arquitetura, seções 4.2 e 4.3).
-- Cobrança reaproveita o billing manual do Servire: o valor fica copiado
-- na contratação, e o pagamento é lançado na própria cobrança.
-- evento_saida nasce aqui para a alteração e o evento ficarem na mesma
-- transação; o job que envia entra no passo da integração.

CREATE TABLE public.cliente (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tipo         varchar(2) NOT NULL CHECK (tipo IN ('PF', 'PJ')),
    documento    varchar(20) NOT NULL UNIQUE,
    nome         varchar(200) NOT NULL,
    logradouro   varchar(200),
    numero       varchar(20),
    complemento  varchar(100),
    bairro       varchar(100),
    cidade       varchar(100),
    uf           varchar(2),
    cep          varchar(8),
    criado_em    timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE public.cliente_contato (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    cliente_id  uuid NOT NULL REFERENCES public.cliente (id),
    nome        varchar(200) NOT NULL,
    email       varchar(200),
    telefone    varchar(30),
    principal   boolean NOT NULL DEFAULT false
);

CREATE TABLE public.produto (
    id                    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    codigo                varchar(40) NOT NULL UNIQUE,
    nome                  varchar(200) NOT NULL,
    url_base_integracao   varchar(300),
    ativo                 boolean NOT NULL DEFAULT true,
    criado_em             timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE public.recurso (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    produto_id  uuid NOT NULL REFERENCES public.produto (id),
    codigo      varchar(60) NOT NULL,
    nome        varchar(200) NOT NULL,
    tipo        varchar(20) NOT NULL CHECK (tipo IN ('LIMITE', 'FUNCIONALIDADE')),
    unidade     varchar(40),
    UNIQUE (produto_id, codigo)
);

CREATE TABLE public.plano (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    produto_id  uuid NOT NULL REFERENCES public.produto (id),
    codigo      varchar(60) NOT NULL,
    nome        varchar(200) NOT NULL,
    ativo       boolean NOT NULL DEFAULT true,
    criado_em   timestamptz NOT NULL DEFAULT now(),
    UNIQUE (produto_id, codigo)
);

CREATE TABLE public.plano_recurso (
    plano_id    uuid NOT NULL REFERENCES public.plano (id),
    recurso_id  uuid NOT NULL REFERENCES public.recurso (id),
    valor       numeric(12, 2) NOT NULL CHECK (valor >= 0),
    PRIMARY KEY (plano_id, recurso_id)
);

CREATE TABLE public.preco_plano (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    plano_id        uuid NOT NULL REFERENCES public.plano (id),
    periodicidade   varchar(10) NOT NULL CHECK (periodicidade IN ('MENSAL', 'ANUAL')),
    valor           numeric(10, 2) NOT NULL CHECK (valor >= 0),
    vigente_desde   date NOT NULL,
    criado_em       timestamptz NOT NULL DEFAULT now(),
    UNIQUE (plano_id, periodicidade, vigente_desde)
);

CREATE TABLE public.adicional (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    produto_id  uuid NOT NULL REFERENCES public.produto (id),
    recurso_id  uuid NOT NULL REFERENCES public.recurso (id),
    codigo      varchar(60) NOT NULL,
    nome        varchar(200) NOT NULL,
    quantidade  numeric(12, 2) NOT NULL CHECK (quantidade > 0),
    preco       numeric(10, 2) NOT NULL CHECK (preco >= 0),
    ativo       boolean NOT NULL DEFAULT true,
    criado_em   timestamptz NOT NULL DEFAULT now(),
    UNIQUE (produto_id, codigo)
);

CREATE TABLE public.contratacao (
    id                          uuid PRIMARY KEY,
    cliente_id                  uuid NOT NULL REFERENCES public.cliente (id),
    produto_id                  uuid NOT NULL REFERENCES public.produto (id),
    plano_id                    uuid NOT NULL REFERENCES public.plano (id),
    periodicidade               varchar(10) NOT NULL CHECK (periodicidade IN ('MENSAL', 'ANUAL')),
    valor                       numeric(10, 2) NOT NULL CHECK (valor >= 0),
    dia_vencimento              integer NOT NULL CHECK (dia_vencimento BETWEEN 1 AND 28),
    inicio                      date NOT NULL,
    vigente_ate                 date,
    situacao_comercial          varchar(20) NOT NULL CHECK (situacao_comercial IN (
                                    'TRIAL', 'ATIVA', 'INADIMPLENTE', 'BLOQUEADA', 'CANCELADA')),
    situacao_provisionamento    varchar(20) NOT NULL DEFAULT 'PENDENTE' CHECK (situacao_provisionamento IN (
                                    'PENDENTE', 'PROCESSANDO', 'ATIVA', 'ERRO')),
    idempotency_key             uuid NOT NULL UNIQUE,
    id_externo                  uuid,
    nome_instancia              varchar(200) NOT NULL,
    slug_instancia              varchar(80) NOT NULL,
    admin_nome                  varchar(200) NOT NULL,
    admin_email                 varchar(200) NOT NULL,
    motivo_bloqueio             text,
    versao_direitos             integer NOT NULL CHECK (versao_direitos > 0),
    direitos_atuais             jsonb NOT NULL,
    observacoes                 text,
    criado_em                   timestamptz NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_contratacao_externo
    ON public.contratacao (produto_id, id_externo) WHERE id_externo IS NOT NULL;

CREATE UNIQUE INDEX uq_contratacao_slug
    ON public.contratacao (produto_id, slug_instancia);

CREATE TABLE public.contratacao_adicional (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    contratacao_id  uuid NOT NULL REFERENCES public.contratacao (id),
    adicional_id    uuid NOT NULL REFERENCES public.adicional (id),
    quantidade      numeric(12, 2) NOT NULL CHECK (quantidade > 0),
    UNIQUE (contratacao_id, adicional_id)
);

CREATE TABLE public.historico_contratacao (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    contratacao_id    uuid NOT NULL REFERENCES public.contratacao (id),
    operador_id       uuid REFERENCES public.operador (id),
    acao              varchar(40) NOT NULL,
    motivo            text,
    versao_direitos   integer NOT NULL,
    criado_em         timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_historico_contratacao
    ON public.historico_contratacao (contratacao_id, criado_em);

CREATE TABLE public.cobranca (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    contratacao_id      uuid NOT NULL REFERENCES public.contratacao (id),
    competencia_inicio  date NOT NULL,
    competencia_fim     date NOT NULL,
    vencimento          date NOT NULL,
    valor               numeric(10, 2) NOT NULL CHECK (valor >= 0),
    status              varchar(20) NOT NULL DEFAULT 'ABERTA' CHECK (status IN ('ABERTA', 'PAGA', 'CANCELADA')),
    pago_em             date,
    valor_pago          numeric(10, 2) CHECK (valor_pago IS NULL OR valor_pago >= 0),
    forma_pagamento     varchar(20),
    observacao          text,
    registrado_por      uuid REFERENCES public.operador (id),
    criado_em           timestamptz NOT NULL DEFAULT now(),
    CHECK (competencia_fim >= competencia_inicio),
    CHECK (status <> 'PAGA' OR (pago_em IS NOT NULL AND valor_pago IS NOT NULL AND forma_pagamento IS NOT NULL)),
    UNIQUE (contratacao_id, competencia_inicio)
);

CREATE INDEX idx_cobranca_contratacao_status_vencimento
    ON public.cobranca (contratacao_id, status, vencimento);

CREATE TABLE public.evento_saida (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    contratacao_id      uuid NOT NULL REFERENCES public.contratacao (id),
    produto_codigo      varchar(40) NOT NULL,
    tipo                varchar(40) NOT NULL,
    versao              integer NOT NULL,
    payload             jsonb NOT NULL,
    situacao            varchar(20) NOT NULL DEFAULT 'PENDENTE' CHECK (situacao IN (
                            'PENDENTE', 'ENVIADO', 'DESCARTADO', 'FALHOU')),
    tentativas          integer NOT NULL DEFAULT 0,
    proxima_tentativa   timestamptz NOT NULL DEFAULT now(),
    criado_em           timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_evento_saida_pendente
    ON public.evento_saida (situacao, proxima_tentativa);

ALTER TABLE public.cliente ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.cliente_contato ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.produto ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.recurso ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.plano ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.plano_recurso ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.preco_plano ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.adicional ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.contratacao ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.contratacao_adicional ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.historico_contratacao ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.cobranca ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.evento_saida ENABLE ROW LEVEL SECURITY;
