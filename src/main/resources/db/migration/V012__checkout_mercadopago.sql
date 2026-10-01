-- Checkout e caixa de notificações: apenas dados mínimos de conciliação.
create table checkout_mercadopago (
 id uuid primary key,
 cobranca_id uuid not null references cobranca(id),
 valor numeric(10,2) not null check(valor>0),
 criado_em timestamptz not null default now(),
 status varchar(20) not null default 'CRIANDO',
 preferencia_id varchar(150), url text,
 reservado_por uuid, reserva_ate timestamptz,
 check ((reservado_por is null) = (reserva_ate is null))
);
create index checkout_mp_cobranca on checkout_mercadopago(cobranca_id);
create table pagamento_mercadopago (
 id varchar(30) primary key,
 revisao bigint not null default 1,
 recebido_em timestamptz not null default now(),
 proxima_tentativa timestamptz not null default now(),
 reservado_por uuid, reserva_ate timestamptz,
 tentativas integer not null default 0,
 situacao varchar(30) not null default 'PENDENTE',
 status_provedor varchar(40),
 checkout_id uuid references checkout_mercadopago(id),
 aplicado boolean not null default false,
 atualizado_em timestamptz,
 check ((reservado_por is null) = (reserva_ate is null))
);
create index pagamento_mp_pendente on pagamento_mercadopago(proxima_tentativa) where situacao='PENDENTE';
alter table checkout_mercadopago enable row level security;
alter table pagamento_mercadopago enable row level security;
do $$ begin
 if exists(select 1 from pg_roles where rolname='anon') then
  revoke all on checkout_mercadopago,pagamento_mercadopago from anon;
 end if;
 if exists(select 1 from pg_roles where rolname='authenticated') then
  revoke all on checkout_mercadopago,pagamento_mercadopago from authenticated;
 end if;
end $$;
