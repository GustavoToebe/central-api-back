CREATE TABLE operador (
    id          uuid PRIMARY KEY,
    nome        varchar(160) NOT NULL,
    email       varchar(180) NOT NULL,
    senha_hash  varchar(120) NOT NULL,
    ativo       boolean      NOT NULL DEFAULT true,
    criado_em   timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT uk_operador_email UNIQUE (email)
);

CREATE TABLE operador_log (
    id           uuid PRIMARY KEY,
    operador_id  uuid,
    acao         varchar(40)  NOT NULL,
    detalhe      varchar(500),
    ip           varchar(64),
    criado_em    timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT fk_operador_log_operador FOREIGN KEY (operador_id) REFERENCES operador (id)
);

CREATE TABLE refresh_token (
    id               uuid PRIMARY KEY,
    operador_id      uuid         NOT NULL,
    token_hash       varchar(64)  NOT NULL,
    expira_em        timestamptz  NOT NULL,
    revogado_em      timestamptz,
    substituido_por  uuid,
    ip               varchar(64),
    criado_em        timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT uk_refresh_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_token_operador FOREIGN KEY (operador_id) REFERENCES operador (id)
);

CREATE TABLE integracao_nonce (
    chave_id     varchar(80) NOT NULL,
    nonce        varchar(80) NOT NULL,
    recebido_em  timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (chave_id, nonce)
);

ALTER TABLE operador ENABLE ROW LEVEL SECURITY;
ALTER TABLE operador_log ENABLE ROW LEVEL SECURITY;
ALTER TABLE refresh_token ENABLE ROW LEVEL SECURITY;
ALTER TABLE integracao_nonce ENABLE ROW LEVEL SECURITY;
