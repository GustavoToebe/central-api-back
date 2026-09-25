# Arquitetura do ecossistema — Central + aplicativos

> Etapa 0 do plano. Documento de decisão, escrito em 25/09/2026 a partir das
> conversas de desenho e do código do `servire-api-back` naquela data
> (baseline V001–V032). O contrato técnico entre os sistemas está em
> [`contrato-integracao-v1.md`](contrato-integracao-v1.md).

## 1. Objetivo

Construir um ecossistema de aplicativos SaaS independentes (Servire hoje;
academia e finanças pessoais/de casal no futuro) com **uma plataforma central
de gestão comercial** — a **Central** (`central-api-back` + `central-api-front`).

A Central cuida de **quem é cliente, o que contratou e se pode usar**. Cada
aplicativo cuida de **quem são os usuários dele, o que cada um pode fazer e
os dados do negócio**.

Princípios:

1. **A Central é a fonte oficial** de clientes, contratações, planos,
   cobranças e direitos comerciais.
2. **O app nunca depende da Central em tempo real.** Login e requisições leem
   uma cópia local; a Central fora do ar não derruba o app (até o limite de
   72h, seção 7.4).
3. **O app recebe "o que pode", nunca "quanto custa".** Limites e
   funcionalidades chegam prontos (plano + adicionais já somados).
4. **A Central não recebe dado de negócio** do app (nome de voluntário,
   criança, aluno). No máximo contagens.
5. **Sem login unificado.** Cada app tem seus usuários, senhas, perfis e
   permissões. A Central nunca guarda senha de usuário de app nem gera token
   de app.
6. **Autenticação de usuário e autenticação entre sistemas são coisas
   separadas** (JWT próprio de cada app × HMAC entre servidores).

## 2. Visão geral

```
                ┌──────────────────────────────────────────────┐
                │ CENTRAL  (central-api-back + central-api-front)│
                │ clientes · produtos · planos · adicionais     │
                │ contratações · cobranças · direitos · log     │
                │ banco próprio (não multi-tenant)              │
                └──────┬───────────────────────────▲────────────┘
   comandos + webhooks │ HMAC                 sync │ HMAC (a cada 8h)
                       ▼                           │
   ┌─────────────────────────────┐   ┌─────────────────────────────┐
   │ SERVIRE (api + front)       │   │ ACADEMIA (futuro)           │
   │ usuários, perfis, senhas    │   │ usuários, perfis, senhas    │
   │ paróquias, escalas, ...     │   │ alunos, treinos, ...        │
   │ direitos_locais (cópia)     │   │ direitos_locais (cópia)     │
   │ banco próprio (multi-tenant)│   │ banco próprio               │
   └─────────────────────────────┘   └─────────────────────────────┘
```

## 3. Responsabilidades

| Assunto | Central | Aplicativo |
|---|---|---|
| Cliente (quem paga), dados fiscais | ✅ dono | — |
| Catálogo de produtos, planos, preços, adicionais | ✅ dono | — |
| Contratação, vencimento, cobrança, pagamento, inadimplência | ✅ dono | — |
| Bloqueio / liberação comercial | ✅ decide | aplica |
| Limites e funcionalidades contratadas | ✅ calcula | aplica (fase futura, seção 10) |
| Criar a instância (paróquia) e o 1º administrador | pede (provisionamento) | ✅ executa |
| Usuários, senhas, convites, perfis, permissões | — | ✅ dono |
| Dados da instância (nome de exibição, endereço, contatos da paróquia) | só nome/slug iniciais | ✅ dono |
| Dados de negócio (pessoas, escalas, inscrições) | — | ✅ dono |
| Sessão de suporte do operador | pede | ✅ emite o próprio token e audita |
| Operadores do SaaS (você) | ✅ dono (login próprio) | — |

## 4. Modelo da Central

### 4.1 Cliente, contratação e instância

Um **cliente** pode ter N **contratações**. Cada contratação é de **um produto**
e gera **uma instância** no app (no Servire, instância = paróquia = tenant),
com **plano, status, vencimento, adicionais e direitos próprios**.

Exemplo — cliente #001 Lucas Fernando:

| Situação | Cliente | Aplicativo | Instância | Plano |
|---|---|---|---|---|
| 1. Contrata o Servire | #001 Lucas | Servire | Paróquia São José | Profissional |
| 2. Contrata a academia | #001 Lucas | Academia | Academia Lucas | Premium |
| 3. Contrata o Servire de novo | #001 Lucas | Servire | Paróquia Santa Maria | Básico |

Se Lucas deixar de pagar a Santa Maria, **só aquela contratação** é bloqueada;
São José e a academia continuam funcionando.

No Servire, Lucas é **um único usuário** (o `usuario` do Servire é global) com
**dois vínculos** (São José e Santa Maria), cada um com seu perfil. Com a Santa
Maria bloqueada, ele entra normalmente e só consegue selecionar a São José.
Na academia ele tem outro login, independente.

### 4.2 Tabelas (proposta)

```
operador, operador_log                     login e histórico dos operadores do SaaS
cliente                                    PF/PJ, documento, endereço, contatos 1:N
produto                                    SERVIRE, ACADEMIA...; url_base_integracao; ativo
recurso                                    produto_id, codigo, tipo (LIMITE | FUNCIONALIDADE), unidade
plano                                      produto_id, codigo, nome, ativo
plano_recurso                              plano_id, recurso_id, valor
preco_plano                                plano_id, periodicidade, valor, vigente_desde   (reaproveita o billing do Servire)
adicional                                  produto_id, recurso_id, quantidade, preço
contratacao                                cliente_id, produto_id, plano_id, periodicidade,
                                           situacao_comercial, dia_vencimento, vigente_ate,
                                           situacao_provisionamento, idempotency_key (única),
                                           id_externo (tenantId no app, único por produto),
                                           nome_instancia, slug_instancia,
                                           admin_nome, admin_email,
                                           versao_direitos, direitos_atuais (jsonb)
contratacao_adicional                      contratacao_id, adicional_id, quantidade
cobranca                                   por contratação, com o pagamento na própria linha
                                           (reaproveita o billing do Servire; não há tabela pagamento)
historico_contratacao                      toda mudança de plano/situação/adicional, com operador e motivo
evento_saida                               outbox dos webhooks (payload, tentativas, próxima tentativa, situação)
integracao_nonce                           nonces recebidos dos apps (anti-repetição)
```

A Central **não é multi-tenant**: só os operadores do SaaS a usam. Sem
`@TenantId`, sem `TenantContext`, sem RLS com policy (só `ENABLE ROW LEVEL
SECURITY` sem policy, para a Data API do Supabase não enxergar as tabelas).

### 4.3 Estados

**Situação comercial da contratação** (vai no snapshot de direitos):

| Situação | Acesso liberado? | Significado |
|---|---|---|
| `TRIAL` | sim | período de teste |
| `ATIVA` | sim | em dia |
| `INADIMPLENTE` | sim | há cobrança vencida, ainda não bloqueada |
| `BLOQUEADA` | **não** | bloqueio comercial (manual, pelo operador) |
| `CANCELADA` | **não** | contrato encerrado |

O bloqueio por atraso continua **manual** (decisão de 23/09/2026): o job diário
só gera cobranças e marca `INADIMPLENTE`; quem bloqueia é o operador. Se um dia
virar automático, muda só a Central — o app obedece `acessoLiberado`.

**Situação do provisionamento:**

| Situação | Significado |
|---|---|
| `PENDENTE` | contratação salva, aguardando envio ao app |
| `PROCESSANDO` | requisição enviada, aguardando resposta |
| `ATIVA` | app confirmou; `id_externo` gravado |
| `ERRO` | falhou; mostra o motivo e o botão "Tentar novamente" |

## 5. Provisionamento

### 5.1 Fluxo (Lucas contrata o Servire)

```
Operador na Central                  Central                                   Servire
────────────────────────────────────────────────────────────────────────────────────────
cadastra cliente #001 Lucas  ─────► cliente
Servire + Profissional + adicionais ► contratacao (PENDENTE), direitos v1,
                                     idempotency_key = contratacao.id
                                     PROCESSANDO
                                     POST /integracao/v1/instancias ────────► 1 transação:
                                     Idempotency-Key: <contratacao.id>          tenant + perfis padrão
                                     {instancia, administrador, direitos v1}    usuário ADMIN (sem senha)
                                                                                ou vínculo, se o e-mail já existe
                                                                                direitos_locais v1
                                                                                integracao_operacao (chave única)
                                                                              depois do commit: convite/aviso
                                     ◄──────────────────── 201 {tenantId} ────
                                     id_externo = tenantId; ATIVA
Lucas abre o e-mail do convite e define a senha no Servire (a Central nunca vê a senha)
```

### 5.2 Garantias contra duplicidade

- A **Idempotency-Key** é o `id` da contratação — a mesma em toda tentativa
  (automática ou botão "Tentar novamente"). Cada tentativa gera **novo**
  horário, nonce e assinatura HMAC.
- O app grava a operação (`integracao_operacao`: chave única, hash do corpo,
  resposta) **na mesma transação** que cria o tenant, os perfis e o
  administrador. Ou tudo existe, ou nada existe.
- Mesma chave + mesmo corpo → o app devolve a resposta gravada, sem criar nada.
- Mesma chave + corpo diferente → `409`, sem repetição automática (erro de programação).
- Duas requisições simultâneas com a mesma chave → a segunda esbarra na
  restrição única, relê a operação gravada e devolve a mesma resposta.
- Slug já usado por outra paróquia → `422`, sem repetição automática; o
  operador corrige e tenta de novo **com a mesma chave** (o app só grava a
  operação quando cria a instância, então a recusa não fica gravada).
- **Edição de nome, slug e administrador** (26/09/2026): só enquanto nenhum
  envio pode ter criado a instância — antes do primeiro POST ou depois de uma
  recusa 4xx que não seja 409. Com 2xx, 5xx, falha de rede ou 409 a instância
  pode existir no app, e um corpo diferente com a mesma chave voltaria 409
  para sempre; a Central recusa com `409 PROVISIONAMENTO_NAO_EDITAVEL`.
- Falha de rede / 5xx / timeout → a Central repete com a mesma chave
  (1 min, 5 min, 15 min, 1 h; depois fica `ERRO` e espera o botão).
- **Convite**: registro próprio (`token_usuario` com finalidade `CONVITE`),
  criado na transação e **enviado depois do commit**. Replay da mesma operação
  não reenvia o e-mail. Reenvio é ação explícita ("Reenviar convite").

### 5.3 Segunda paróquia do mesmo cliente

Mesmo fluxo. Se o e-mail do administrador **já existe** no Servire, o app só
cria o vínculo (perfil Administrador da nova paróquia) e envia um **aviso de
acesso concedido** — sem convite de senha.

## 6. Autenticação

### 6.1 Usuário × sistema

| | Usuário (ex.: Lucas no Servire) | Sistema (Central ↔ app) |
|---|---|---|
| Quem prova identidade | pessoa, com e-mail e senha | servidor, com segredo compartilhado |
| Quem valida | o próprio app | o lado que recebe |
| Credencial | JWT do app (+ refresh em cookie) | assinatura HMAC por requisição |
| Rotas | `/auth/**`, rotas de negócio | `/integracao/v1/**` (cadeia de filtros própria) |

Nenhum JWT de usuário recebe a permissão de integração, e a assinatura HMAC
não abre rota de usuário.

### 6.2 HMAC com proteção contra repetição

Detalhes no contrato. Resumo das três camadas:

1. **Horário**: mensagem com mais de 5 minutos de diferença é recusada.
2. **Nonce**: cada envio tem um UUID novo; o receptor grava e recusa reuso
   (guarda por 10 minutos, o dobro da janela).
3. **Versão do estado**: snapshot de direitos com versão menor ou igual à local
   é ignorado — mensagem antiga ou fora de ordem nunca reverte o estado.

Além disso: HTTPS sempre, segredos diferentes **por aplicativo e por direção**,
identificador de chave no header para permitir rotação sem parada.

### 6.3 Sessão de suporte

A Central pede ao app um **código de uso único** (válido por 2 minutos). O
navegador do operador abre `https://<app>/suporte?codigo=...` e o app troca o
código pelo **próprio** JWT de suporte, registrando na auditoria quem entrou,
em qual paróquia e por quê. A Central nunca conhece o segredo JWT do app.
Suporte continua aceitando instância bloqueada (é para isso que ele existe).

## 7. Direitos e sincronização

### 7.1 Cópia local

Cada app guarda, por instância, a última versão dos direitos
(`direitos_locais`): situação, `acessoLiberado`, vencimento (informativo),
plano, limites, funcionalidades, versão e `confirmadoEm` (última vez que a
Central confirmou esse estado, por webhook ou sincronização).

Exemplo no Servire:

| Campo | Valor |
|---|---|
| Instância | Paróquia São José |
| Plano | Profissional |
| Situação | ATIVA |
| Limite de voluntários | 100 |
| Armazenamento | 5 GB |
| Confirmado em | 08:00 |

### 7.2 Três mecanismos

| Mecanismo | Quando | Para quê |
|---|---|---|
| **Webhook** (Central → app) | a cada alteração | atualizar na hora (bloqueio às 09:00 vale às 09:00) |
| **Sincronização** (app → Central) | a cada 8h e quando a API sobe | recuperar alteração perdida; confirmar o estado |
| **Cópia local** | todo login e toda requisição protegida | funcionar sem depender da Central |

- A Central grava o novo snapshot e o `evento_saida` **na mesma transação**
  da alteração (outbox). Um job envia; falhou, reenvia com espera crescente.
- A sincronização de 8h busca **todos** os snapshots do produto (são poucos)
  e aplica os de versão maior; mesmo quando nada mudou, atualiza `confirmadoEm`.
- O Kill Switch que já existe no Servire (revalida o tenant a cada
  requisição) passa a derivar o status da cópia local. Nenhuma chamada de rede
  no caminho da requisição.
- Login: lê a cópia local. Instância sem acesso → mensagem clara e a paróquia
  some da lista de seleção; as outras paróquias do usuário seguem normais.

### 7.3 Regra de acesso no app

```
acesso liberado = direitos.acessoLiberado
                  E (agora − direitos.confirmadoEm) ≤ tolerância técnica (72h)
```

O vencimento (`vigenteAte`) é **informativo** no app (ex.: aviso "vence em 5
dias"); quem transforma atraso em bloqueio é a Central.

### 7.4 Central indisponível (decisão: bloquear após 72h)

| Tempo sem confirmação | Comportamento do app |
|---|---|
| até 24h | normal |
| 24h | log de erro + e-mail de alerta para o operador; continua normal |
| 72h | **bloqueia** as instâncias não confirmadas até a Central responder |

- A contagem é por instância, a partir da última confirmação bem-sucedida;
  cada sincronização de 8h que funciona zera o relógio.
- **Chave de emergência**: a tolerância é uma configuração do app
  (`..._TOLERANCIA_HORAS`, padrão 72), para estender se a Central quebrar num
  fim de semana.
- Instância que já estava bloqueada continua bloqueada com a Central fora.
- Efeito colateral útil: a sincronização periódica mantém o banco da Central
  em uso (Supabase free pausa projeto parado).

## 8. Servire — o que muda

### 8.1 Sai do Servire (vai para a Central ou deixa de existir)

- `billing/` inteiro (planos, preços, assinatura, cobrança, `BillingJob`) → Central.
- `backoffice/` inteiro (`/admin/**`, login de operador, lista de paróquias,
  logins, `backoffice_log`, sessão de suporte que gera JWT) → Central.
- `usuario.operador_saas` e o `DevOperadorSeed`.
- Tabelas `plano`, `preco_plano`, `assinatura`, `cobranca`, `backoffice_log`.
- Telas de backoffice do `servire-api-front` → `central-api-front`.

### 8.2 Entra no Servire

| Item | Descrição |
|---|---|
| `perfil`, `perfil_permissao` | perfis por paróquia; `usuario_tenant.role` vira `perfil_id` |
| Catálogo de permissões no código | seção → módulo → ações; `GET /permissoes/catalogo` |
| `/perfis`, `/usuarios`, `/me` | telas no estilo SIN+ (seções 8.3 e 8.4) |
| `token_usuario` | generaliza o reset de senha: finalidade `RESET` ou `CONVITE` |
| `usuario.telefone`, `usuario.tipo_telefone` | contato do usuário |
| `direitos_locais` | cópia dos direitos por tenant |
| `integracao_operacao` | idempotência do provisionamento |
| `integracao_nonce` | anti-repetição do HMAC |
| `suporte_codigo` | código de uso único da sessão de suporte |
| `/integracao/v1/**` | cadeia de filtros própria com HMAC |

### 8.3 Perfis e permissões (modelo SIN+)

**Tela Perfis** (menu Cadastro → Perfil do usuário): busca, filtro de status,
**Opções** (Ativar, Inativar, Duplicar), colunas Perfil / Usuários
(quantidade) / Status, paginação, botão **Novo perfil**.

**Tela Editar Perfil:**

```
Editar Perfil #3                                         Perfil ativo: [●]
Nome do perfil*: [Secretário                          ]
Usuários (2)                                          ADICIONAR USUÁRIO
  Usuários adicionados aqui terão este perfil; um usuário de outro perfil
  tem o perfil substituído por este.
  Maria Silva      maria@...
  João Souza       joao@...
[ ] Liberar todas as opções
▾ Cadastro                                                [ ] Marcar todos
  [✓] PERFIL DO USUÁRIO   [✓] USUÁRIO            [✓] PARÓQUIA   [✓] PESSOAS
      [✓] Criar               [✓] Criar              [✓] Alterar    [✓] Criar
      [✓] Alterar             [✓] Alterar                           [✓] Alterar
                              [✓] Reenviar convite                  [✓] Excluir
                                                                    [✓] Ativar/Inativar
▾ Escalas                                                 [ ] Marcar todos
  [✓] ESCALA              [✓] VAGAS
      [✓] Criar               [✓] Alocar voluntário
      [✓] Alterar             [✓] Registrar presença
      [✓] Excluir
      [✓] Finalizar/Reabrir
      [✓] Cancelar
▾ Inscrições                                              [ ] Marcar todos
  [✓] INSCRIÇÃO
      [✓] Alterar
      [✓] Aprovar
      [✓] Rejeitar
▾ Relatórios                                              [ ] Marcar todos
  [✓] AUDITORIA
                                              CANCELAR   SALVAR PERFIL
```

Comportamento da matriz:

- O checkbox do **módulo** é o acesso/visualizar (libera os `GET`).
- Desmarcar o módulo desmarca e desabilita as ações; marcar uma ação marca o módulo.
- "Marcar todos" por seção.
- **"Liberar todas as opções"** = `acesso_total`: desabilita a grade e libera
  tudo, inclusive permissões criadas no futuro.
- As travas por canal do SIN+ (web/app) ficam de fora: o Servire só tem web.

**Catálogo** (códigos; cada um vira `PERM_<CODIGO>` no `@PreAuthorize`):

| Seção | Módulo (acesso) | Ações |
|---|---|---|
| Cadastro | `PERFIL` | `PERFIL_CRIAR`, `PERFIL_ALTERAR` |
| Cadastro | `USUARIO` | `USUARIO_CRIAR`, `USUARIO_ALTERAR`, `USUARIO_REENVIAR_CONVITE` |
| Cadastro | `PAROQUIA` | `PAROQUIA_ALTERAR` |
| Cadastro | `PESSOA` | `PESSOA_CRIAR`, `PESSOA_ALTERAR`, `PESSOA_EXCLUIR`, `PESSOA_ATIVAR_INATIVAR` |
| Escalas | `ESCALA` | `ESCALA_CRIAR`, `ESCALA_ALTERAR`, `ESCALA_EXCLUIR`, `ESCALA_FINALIZAR_REABRIR`, `ESCALA_CANCELAR` |
| Escalas | `VAGA` | `VAGA_ALOCAR`, `VAGA_PRESENCA` |
| Inscrições | `INSCRICAO` | `INSCRICAO_ALTERAR`, `INSCRICAO_APROVAR`, `INSCRICAO_REJEITAR` |
| Relatórios | `AUDITORIA` | — |

Foto e disponibilidade do voluntário entram em `PESSOA_ALTERAR`; o picker de
candidatos da escala, em `ESCALA`.

**Perfis criados em toda paróquia nova:**

| Perfil | Permissões |
|---|---|
| Administrador | `acesso_total` (perfil de sistema) |
| Secretário | Pessoas (tudo), Paróquia (visualizar), Inscrições (tudo), Escalas (visualizar) |
| Coordenador | Escalas e Vagas (tudo), Pessoas (visualizar), Inscrições (visualizar) |

**Regras de proteção:**

- **Administrador** é perfil de sistema: sempre com "Liberar todas as
  opções"; não pode ser desmarcado, inativado nem excluído. O nome pode mudar.
- Cada paróquia mantém **pelo menos um usuário ativo em perfil com acesso
  total**. Inativar, trocar de perfil ou mover o último é recusado com mensagem.
- Perfil com usuários ativos não pode ser inativado sem antes mover os usuários.
- **Um perfil por usuário em cada paróquia** (como no SIN+: adicionar um
  usuário a um perfil substitui o anterior).
- Permissões são carregadas por requisição (o filtro já lê `usuario_tenant`
  para o Kill Switch), com cache curto.

### 8.4 Usuários

**Tela Usuários** (menu Cadastro → Usuário): busca, status, **Opções**
(Ativar, Inativar, Reenviar convite), colunas Usuário (avatar com iniciais) /
Perfil / E-mail / Telefone / Status, paginação, botão **Novo usuário**.

**Tela Editar Usuário:**

```
Editar Usuário                                          Usuário ativo: [●]
 (GT)   Nome completo*: [Gustavo Cesar Toebe  ]  E-mail*: [gustavo@...      ]
        Tipo tel.: [Celular]  Telefone: [(45) 99810-8716]  Perfil*: [Secretário ▾]
        Situação do acesso: ✓ Senha definida  |  ⏳ Convite enviado em 25/09 [REENVIAR]
                                             CANCELAR   SALVAR USUÁRIO
```

Decisões:

- **Sem campo de senha.** O usuário sempre define a própria senha pelo
  convite; o admin só tem "Reenviar convite" / "Enviar redefinição de senha".
- **Novo usuário com e-mail inexistente** → cria e envia convite. **E-mail já
  existente** (usa outra paróquia) → só cria o vínculo com o perfil e envia
  aviso de acesso. A tela **não revela** as outras paróquias da pessoa.
- **Usuário ativo** e **perfil** valem só para a paróquia atual
  (`usuario_tenant`). Inativar na Santa Maria não afeta a São José.
- **Nome, e-mail e telefone** só são editáveis pelo admin se o usuário tiver
  vínculo **apenas com a paróquia dele**; senão ficam somente leitura. Cada
  usuário edita os próprios dados e a senha em **"Meu perfil"** (`/me`).
- **Avatar só com iniciais** agora; foto fica para depois.
- Sem seção "Paróquias" no usuário (diferente de "Condomínios" do SIN+): cada
  paróquia é uma contratação separada e controla o próprio vínculo.
- O cabeçalho do front mostra o perfil embaixo do nome do usuário.

## 9. Bancos de dados

**Servire**: continua no Supabase atual. ~~Baseline novo~~ **Mudou na
implementação (25/09/2026):** em vez de substituir V001–V032 por um `V001`
limpo, o modelo novo entrou em migrations **aditivas** (V035 perfis e
convite, V036 integração v1), para não mexer na produção antes do corte. As
tabelas de billing/backoffice (`plano`, `preco_plano`, `assinatura`,
`cobranca`, `backoffice_log`) e a coluna `usuario.operador_saas` continuam no
banco, **sem código**, até a Central existir para recebê-las; uma migration de
limpeza apaga tudo no corte. O recadastro manual das 3 crianças continua.

**Central**: segundo projeto free do Supabase, banco próprio, Flyway próprio.

**Backup**: fora de escopo por enquanto (só uma usuária). Ao chegar a 2
clientes pagantes: Supabase Pro + VPS maior.

Nunca há join entre bancos. Ligação só por identificadores: a Central guarda o
`tenantId` do app em `contratacao.id_externo`; o app guarda o `contratacaoId`
em `direitos_locais`.

## 10. Limites e redução de plano (modelado agora, aplicado depois)

- **Agora**: a Central cadastra recursos, limites por plano e adicionais,
  calcula o total e envia no snapshot; o Servire guarda na cópia local e
  **não bloqueia nada** ainda.
- **Depois** (quando houver cliente pagante): o app aplica.
  - Criar além do limite → recusado com código `LIMITE_PLANO`.
  - Funcionalidade desligada → recusada com código próprio (inclui a rota
    pública de inscrição).
  - **Redução de plano**: os dados **ficam**, mas os registros excedentes não
    podem ser acessados, editados nem excluídos, e nada novo pode ser salvo
    até o cliente voltar ao limite ou contratar mais. (Detalhar na época quais
    registros são "excedentes" e como fica a escala que referencia um deles.)
  - Armazenamento exige guardar o tamanho de cada arquivo (hoje não guarda).
  - "Quantidade de paróquias" **não** é limite: cada paróquia é uma contratação.

## 11. Plano de implementação

| Etapa | Onde | Entrega |
|---|---|---|
| **0. Desenho** | `central-api-back/docs` | este documento + `contrato-integracao-v1.md` |
| **1. Servire: acesso** | servire back + front | baseline `V001` novo sem billing/backoffice; perfis e permissões por módulo; usuários, convite e "Meu perfil"; telas Perfis e Usuários; remove telas de backoffice; atualiza AGENTS/README/testes |
| **2. Servire: integração** | servire back | `direitos_locais` + Kill Switch derivado + regra das 72h e alerta; filtro HMAC com nonce; provisionamento idempotente; webhook de direitos; código de suporte; job de sincronização de 8h |
| **3. Central: back** | `central-api-back` | operadores e login; clientes, produtos, recursos, planos, preços, adicionais; contratações com cálculo de direitos versionado; provisionamento com estados e repetição; outbox de webhooks; endpoint de sincronização; cobrança manual (reaproveitada do Servire); histórico |
| **4. Central: front** | `central-api-front` | clientes; grade de contratações; planos e adicionais; cobranças; "Tentar novamente"; entrar em suporte |
| **5. Corte** | produção | recria o banco do Servire pelo baseline (trocando a senha do banco); sobe a Central na VPS; cadastra a cliente #001; provisiona a paróquia; convite; recadastro das 3 crianças; apaga as fotos órfãs do bucket |
| **Depois** | — | aplicar limites e redução de plano; foto do usuário; gateway de pagamento; biblioteca compartilhada (acesso + cliente da Central) quando vier o 2º app |

As etapas 1 e 2 são validadas sozinhas (testes de integração chamando
`/integracao/v1` com requisições assinadas) antes da Central existir. A
produção atual do Servire fica intacta até o corte.

Reaproveitamento entre apps: o módulo de acesso (usuários/perfis) e o
"cliente da Central" (HMAC, cópia local, sync) nascem em pacotes isolados no
Servire e são **copiados** para o 2º app; biblioteca Maven só se, no 3º, as
cópias continuarem iguais.

## 12. Riscos

1. **Bloqueio após 72h**: se a Central (ou o banco free dela) cair num fim de
   semana e o alerta passar batido, o Servire para. Mitigação: alerta às 24h +
   chave de emergência.
2. **Escopo**: duas etapas grandes no Servire e um sistema novo. A Central
   começa só com o fluxo manual.
3. **Baseline novo**: durante o trabalho some a proteção de migration aplicada.
4. **Divergência Central × app** por webhook perdido: versão + sync de 8h +
   relatório de conciliação (tenant sem contratação e vice-versa).
5. **A Central é o sistema mais sensível** (bloqueia e cria em todos os apps):
   segredos separados, acesso restrito, 2FA dos operadores no futuro.
6. **Segredos**: a senha do banco do Servire já vazou uma vez no histórico
   (`.env.example`); trocar no corte. Segredos só em env var.
7. **Perfis customizáveis**: risco de a paróquia se trancar para fora — coberto
   pelo perfil de sistema e pela regra do administrador mínimo.
8. **Supabase free** (pausa, limites de conexão) e **VPS** (duas JVMs): pool
   Hikari pequeno e heap limitado.

## 13. Decisões registradas (25/09/2026)

| # | Decisão |
|---|---|
| 1 | Central separada (API + banco próprios), nome `central-api-back` / `central-api-front` |
| 2 | Cliente 1:N contratações; contratação = produto + instância + plano + situação próprios |
| 3 | Sem login unificado; cada app com usuários, senhas, perfis e permissões próprios |
| 4 | Webhook + sync de 8h + cópia local; login não depende da Central |
| 5 | Central indisponível por mais de 72h → **bloqueia** o acesso |
| 6 | HMAC com horário (5 min), nonce e versão; chaves por app e direção |
| 7 | Provisionamento idempotente (chave = id da contratação); convite depois do commit |
| 8 | Limites e adicionais modelados agora, aplicados depois; redução de plano mantém os dados sem acesso |
| 9 | Perfis customizáveis por paróquia no modelo SIN+; Administrador protegido; ≥ 1 admin ativo |
| 10 | Um perfil por usuário em cada paróquia |
| 11 | Sem campo de senha: só convite / redefinição |
| 12 | Nome/e-mail de usuário com outras paróquias: só leitura para o admin; o próprio usuário edita em "Meu perfil" |
| 13 | Avatar só com iniciais por enquanto |
| 14 | Servire com banco recriado no corte e recadastro manual das 3 crianças (migrations aditivas V035/V036 em vez de baseline novo — seção 9) |
| 15 | Sem backup por enquanto; Supabase Pro + VPS maior a partir de 2 clientes |
| 16 | Bloqueio por atraso continua manual (decisão de 23/09/2026) |
| 17 | Commits dos repositórios da Central direto na `main` |
| 18 | Diocese é só agrupamento informativo no Servire (sem cota); diocese que contratar em bloco vira cliente na Central |
| 19 | Idempotency-Key é sempre o id da contratação; nome, slug e administrador só são editáveis antes de um envio que possa ter criado a instância (26/09/2026) |
| 20 | Testes na mesma versão major do Postgres da produção: 17 (Supabase 17.6, 26/09/2026) |

## 14. Estado da implementação

### 14.1 Servire (25/09/2026)

Etapas 1 e 2 implementadas no `servire-api-back` (commit `bb42ecc` e a
revisão seguinte). Diferenças e detalhes em relação ao desenho acima:

| Tema | Como ficou |
|---|---|
| Migrations | Aditivas (V035/V036), não baseline novo — ver seção 9 |
| Permissões | O catálogo da seção 8.3 é a única lista (`CatalogoPermissao`); cada endpoint pede a ação (`PERM_ESCALA_EXCLUIR`, `PERM_VAGA_PRESENCA`…) e o módulo libera a leitura. Ação marcada traz o módulo junto; código fora do catálogo é recusado |
| Concessão | Quem não tem acesso total não concede acesso total, nem permissão que não tem, nem altera quem tem acesso total |
| Convite | Token de uso único com finalidade `CONVITE`, válido por 7 dias (reset: 1h). Página `/reset-password?token=` no front serve aos dois |
| `/integracao/**` | O HMAC concede `PERM_INTEGRACAO`, exigida pelo controller; a rota não é pública. Nonce com `INSERT ... ON CONFLICT DO NOTHING` |
| Regra de acesso | Uma só (`AcessoParoquia`), usada no login e em toda requisição: sem `direitos_locais` vale o status do tenant; com a linha, `acessoLiberado` + 72h. Perfil inativo também barra |
| Alerta | Roda depois de toda tentativa de sincronização (inclusive falha), no máximo um e-mail a cada 8h |
| Suporte | Código de uso único (`UPDATE ... WHERE usado_em IS NULL`); a entrada é auditada na paróquia |
| Operador no app | Removido: token `backoffice`, suporte antigo e login/refresh do operador |
| Diocese | **Só agrupamento informativo** (decisão de 25/09/2026, V037): sem cota. A paróquia escolhe ou digita a diocese na tela Paróquia (`PUT /tenant`); `GET /dioceses` sugere as já usadas. Não é assunto da Central — se uma diocese contratar em bloco, vira **cliente** aqui, com cada paróquia como contratação |

### 14.2 Central (26/09/2026)

Etapa 3 implementada no `central-api-back` (fundação `9deff57`, domínio
comercial `e5c77fb`, integração `3c52b50` e o ajuste de 26/09/2026).
Diferenças e detalhes em relação ao desenho acima:

| Tema | Como ficou |
|---|---|
| Migrations | V001 (operador e nonce), V002 (domínio comercial), V003 (`ultimo_status_provisionamento`). RLS sem policy nas 17 tabelas |
| Operadores | Login, refresh em cookie httpOnly `central_refresh_token` (path `/auth`, `Secure`, `SameSite=None`), logout, JWT próprio. Seed só no profile `dev` |
| CSRF | Métodos seguros não exigem; escrita em `/auth` exige; Bearer fora de `/auth` não exige; anônimo sem o cookie de refresh recebe 401. Domínio do `XSRF-TOKEN` em `CENTRAL_CSRF_COOKIE_DOMAIN` (painel e API em subdomínios: o domínio pai), obrigatória em produção |
| Pagamento | Mora na `cobranca` (não há tabela `pagamento`) |
| Troca de plano | Atualiza a mesma contratação: cobranças pagas ficam; abertas a partir da data da troca são apagadas e geradas de novo |
| Situação comercial | Pagar tudo leva `INADIMPLENTE` e `TRIAL` para `ATIVA`; `BLOQUEADA` continua bloqueada. Isentar a última vencida devolve `INADIMPLENTE` para `ATIVA`. Job diário às 03:00 (America/Sao_Paulo) gera cobranças e marca `INADIMPLENTE`; não bloqueia |
| Outbox | Job a cada minuto (`central.entrega.job.enabled`). Provisionar: 1 min, 5 min, 15 min, 1 h e então `ERRO`. Webhook: 1 min, 5 min, 15 min, 1 h, 3 h e depois a cada 6 h até 72 h; então `FALHOU` com alerta em log de erro (a Central não manda e-mail) |
| Repetição | 2xx encerra (inclusive `aplicado: false`). 409 e 422 não repetem. Rede, timeout, 5xx **e também 400, 401 e 404** repetem até o fim da agenda (divergência do contrato v1, seção 3: esses códigos costumam ser configuração, que se corrige sem mexer na contratação) |
| Idempotency-Key | Sempre o id da contratação. A edição de nome, slug e administrador só vale antes de um envio que possa ter criado a instância (seção 5.2); a resposta da contratação traz `provisionamentoEditavel` |
| Operador | `POST /contratacoes/{id}/tentar-provisionamento` depois de `ERRO` (recusa se provisionada ou cancelada); `POST /contratacoes/{id}/suporte` devolve `{codigo, urlAcesso, expiraEm}` e grava `operador_log` |
| Sincronização | `GET /integracao/v1/produtos/{produto}/direitos?pagina&tamanho` (tamanho 1 a 100, padrão 100): só contratações com `id_externo`, inclusive bloqueadas e canceladas |
| Painel | Listas não paginadas por enquanto; `GET` por id em cliente e contratação |
| Variáveis | Em produção sem valor padrão: `CENTRAL_JWT_SEGREDO`, `CORS_ALLOWED_ORIGINS`, `CENTRAL_CSRF_COOKIE_DOMAIN` e as `CENTRAL_PRODUTO_SERVIRE_*` |
