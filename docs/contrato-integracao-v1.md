# Contrato de integração v1 — Central ↔ aplicativos

> Etapa 0. Versão 1 do contrato, 25/09/2026. Visão geral e motivos em
> [`arquitetura-central.md`](arquitetura-central.md). Os exemplos usam o
> Servirea; outro app implementa o mesmo contrato trocando o produto e os
> campos específicos da instância.

## 1. Convenções

- Tudo em **HTTPS**, JSON UTF-8, datas-hora em ISO-8601 UTC
  (`2026-09-25T12:00:00Z`), datas em `YYYY-MM-DD`, IDs em UUID.
- Prefixo `/integracao/v1`. Mudanças **só aditivas** dentro da v1 (campo novo,
  endpoint novo). Os dois lados **ignoram campos desconhecidos**. Mudança
  incompatível = `/integracao/v2` convivendo com a v1 até a migração.
- Rotas de integração não usam cookie nem JWT de usuário, então não há CSRF.
- Endereços (provisórios):
  - Central: `https://central.servirea.com.br`
  - Servirea: `https://api.servirea.com.br`
- Os relógios dos servidores ficam sincronizados por NTP (a janela de horário
  depende disso).

## 2. Autenticação entre sistemas (HMAC)

### 2.1 Chaves

- Um segredo **por aplicativo e por direção**:
  - `central → servire`: a Central assina, o Servirea valida.
  - `servire → central`: o Servirea assina, a Central valida.
- Segredo: no mínimo 32 bytes aleatórios, em Base64, **só em env var** (nunca
  no git, nunca no `.env.example`).
- Cada segredo tem um **identificador** (ex.: `central-servire-2026a`) enviado
  no header. O receptor aceita até **duas** chaves ativas por direção, para
  trocar sem parada: cadastra a nova, o emissor passa a usá-la, remove a velha.

Variáveis de ambiente propostas:

| Onde | Variável | Conteúdo |
|---|---|---|
| Servirea | `SERVIRE_INTEGRACAO_CHAVES_ENTRADA` | `id:segredoBase64[,id:segredoBase64]` (chaves que a Central usa) |
| Servirea | `SERVIRE_INTEGRACAO_CHAVE_SAIDA_ID` / `..._SEGREDO` | chave com que o Servirea assina |
| Servirea | `SERVIRE_INTEGRACAO_CENTRAL_URL` | URL base da Central |
| Servirea | `SERVIRE_INTEGRACAO_TOLERANCIA_HORAS` | tolerância técnica (padrão 72) |
| Servirea | `SERVIRE_INTEGRACAO_ALERTA_EMAIL` | quem recebe o alerta de 24h sem confirmação |
| Central | `CENTRAL_PRODUTO_SERVIRE_CHAVES_ENTRADA` | chaves que o Servirea usa |
| Central | `CENTRAL_PRODUTO_SERVIRE_CHAVE_SAIDA_ID` / `..._SEGREDO` | chave com que a Central assina para o Servirea |

### 2.2 Headers

| Header | Exemplo | Regra |
|---|---|---|
| `X-Integracao-Chave` | `central-servire-2026a` | identificador da chave usada |
| `X-Integracao-Timestamp` | `1790000000` | segundos Unix (UTC) do envio |
| `X-Integracao-Nonce` | `5b1f2a0e-8c3d-4f6a-9b7e-1d2c3b4a5f60` | UUID **novo a cada envio**, inclusive em repetições |
| `X-Integracao-Assinatura` | `v1=4c63e6…` | `v1=` + HMAC-SHA256 em hex minúsculo |
| `Idempotency-Key` | `0f8e2c1a-…` | só no provisionamento; **igual** em toda repetição |

### 2.3 Texto assinado

```
<MÉTODO>\n<CAMINHO>\n<TIMESTAMP>\n<NONCE>\n<SHA256_HEX(CORPO)>
```

- `MÉTODO` em maiúsculas (`POST`).
- `CAMINHO` = caminho + query string **exatamente como enviados** na linha de
  requisição (percent-encoded), sem esquema nem host
  (`/integracao/v1/produtos/SERVIREA/direitos?pagina=0`).
- `SHA256_HEX(CORPO)` = SHA-256 dos **bytes exatos** do corpo, em hex
  minúsculo; corpo vazio → hash da string vazia
  (`e3b0c442…b855`).
- Separador `\n` (LF), sem `\n` no final.
- Assinatura = `HMAC-SHA256(chave = bytes do segredo após decodificar o Base64, mensagem = bytes UTF-8 do texto acima)`.

Método e caminho entram na assinatura para que um corpo assinado não sirva em
outra rota; o hash do corpo garante que nada foi alterado.

### 2.4 Validação no receptor (nesta ordem)

1. Chave desconhecida → `401 CHAVE_DESCONHECIDA`.
2. `|agora − timestamp| > 300 s` → `401 TIMESTAMP_FORA_DA_JANELA`.
3. Assinatura diferente (comparação em **tempo constante**) → `401 ASSINATURA_INVALIDA`.
4. Grava `(chave, nonce)` numa tabela com restrição única, **em transação
   própria** (antes do handler; persiste mesmo que o handler falhe). Já existe →
   `401 NONCE_REPETIDO`. O nonce só é gravado depois da assinatura válida, para
   ninguém lotar a tabela sem conhecer o segredo.
5. Job apaga nonces com mais de 10 minutos (o dobro da janela).

A resposta não revela qual etapa falhou além do código; o log registra os detalhes.

### 2.5 Vetores de teste

Os dois repositórios têm teste unitário com estes vetores (segredo **de teste**,
em texto puro, sem Base64 — o teste usa os bytes UTF-8 da string):

Segredo: `segredo-de-teste-nao-usar-em-producao-0123456789`

**Vetor 1 — POST com corpo**

```
MÉTODO     POST
CAMINHO    /integracao/v1/instancias
TIMESTAMP  1790000000
NONCE      5b1f2a0e-8c3d-4f6a-9b7e-1d2c3b4a5f60
CORPO      {"contratacaoId":"0f8e2c1a-1111-4a2b-9c3d-000000000001"}
SHA256     c1289f0c4d760055dd35521a90bdb3fbd73807fbeff733378963662a77a34223
ASSINATURA v1=4c63e60805c925d7c3cd321c8726e984ff1202ba6e62df81c6f5749694f99f32
```

**Vetor 2 — GET sem corpo**

```
MÉTODO     GET
CAMINHO    /integracao/v1/produtos/SERVIRE/direitos
TIMESTAMP  1790000300
NONCE      a7c9e1f2-2222-4b3c-8d4e-000000000002
CORPO      (vazio)
SHA256     e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
ASSINATURA v1=976359d02a3d078c41896cff976ba58fcb6cad78060392fbc43b676a95f8b113
```

## 3. Erros

Mesmo formato do `ApiError` do Servirea, com um campo novo `codigo` (estável,
para a máquina decidir) além da `message` (para humanos):

```json
{
  "timestamp": "2026-09-25T12:00:00Z",
  "status": 422,
  "error": "Unprocessable Entity",
  "codigo": "SLUG_EM_USO",
  "message": "Já existe uma paróquia com este slug.",
  "path": "/integracao/v1/instancias",
  "requestId": "…",
  "fieldErrors": []
}
```

| HTTP | `codigo` | Emissor repete automaticamente? |
|---|---|---|
| 401 | `CHAVE_DESCONHECIDA`, `TIMESTAMP_FORA_DA_JANELA`, `ASSINATURA_INVALIDA`, `NONCE_REPETIDO` | **sim**, até o fim da agenda, com alerta (configuração ou relógio: corrigido o ambiente, a próxima tentativa passa) |
| 400 | `DADOS_INVALIDOS` (Bean Validation, com `fieldErrors`) | **sim**, até o fim da agenda |
| 404 | `INSTANCIA_NAO_ENCONTRADA` | **sim**, até o fim da agenda |
| 409 | `IDEMPOTENCY_KEY_CONFLITO` | não |
| 422 | `SLUG_EM_USO` | não (operador corrige) |
| 5xx, timeout, rede | — | **sim**, com espera crescente |

## 4. Snapshot de direitos (`DireitosInstancia`)

Estrutura enviada no provisionamento, no webhook e na sincronização. É sempre
o **estado completo**, nunca uma diferença.

```json
{
  "contratacaoId": "0f8e2c1a-1111-4a2b-9c3d-000000000001",
  "clienteId": "9d3b7a10-3333-4c4d-8e5f-000000000003",
  "produto": "SERVIREA",
  "tenantId": "25c1d0e4-4444-4d5e-9f60-000000000004",
  "versao": 18,
  "situacao": "ATIVA",
  "acessoLiberado": true,
  "motivoBloqueio": null,
  "vigenteAte": "2026-10-15",
  "plano": { "codigo": "PROFISSIONAL", "nome": "Profissional" },
  "limites": { "voluntarios": 100, "usuarios": 5, "armazenamento_mb": 5120 },
  "funcionalidades": ["ESCALAS", "INSCRICAO_PUBLICA"],
  "geradoEm": "2026-09-25T12:00:00Z"
}
```

| Campo | Regra |
|---|---|
| `tenantId` | `null` no provisionamento (ainda não existe); preenchido depois |
| `versao` | inteiro, cresce a cada mudança da contratação; o app só aplica versão **maior** que a local |
| `situacao` | `TRIAL` · `ATIVA` · `INADIMPLENTE` · `BLOQUEADA` · `CANCELADA` |
| `acessoLiberado` | decisão da Central; o app obedece (junto com a regra das 72h) |
| `motivoBloqueio` | texto curto para a mensagem de bloqueio no app (`null` se liberado) |
| `vigenteAte` | **informativo** no app (aviso de vencimento); não bloqueia sozinho |
| `limites` | mapa `codigo → número`, já com adicionais somados. **Aplicação futura** |
| `funcionalidades` | códigos ligados. **Aplicação futura** |

Regra de acesso no app:

```
liberado = acessoLiberado  E  (agora − confirmadoEm) ≤ tolerância técnica (72h)
```

`confirmadoEm` = última vez que a Central confirmou o estado daquela instância
(webhook aplicado **ou** presente na sincronização, mesmo com versão igual).

## 5. Central → app

### 5.1 Provisionar instância

`POST /integracao/v1/instancias` — exige `Idempotency-Key` (= `contratacaoId`).

```json
{
  "contratacaoId": "0f8e2c1a-1111-4a2b-9c3d-000000000001",
  "clienteId": "9d3b7a10-3333-4c4d-8e5f-000000000003",
  "instancia": {
    "nome": "Paróquia São José",
    "slug": "sao-jose"
  },
  "administrador": {
    "nome": "Lucas Fernando",
    "email": "lucas@exemplo.com.br"
  },
  "direitos": { "…": "DireitosInstancia, com tenantId null" }
}
```

O app, **numa transação**:

1. Grava `integracao_operacao` (chave única + SHA-256 do corpo).
2. Cria o tenant (nome, slug; demais dados da paróquia o admin preenche no Servirea).
3. Cria os perfis padrão (Administrador, Secretário, Coordenador).
4. Usuário do administrador: e-mail inexistente → cria sem senha + token
   `CONVITE`; e-mail existente → só cria o vínculo.
5. Vincula o usuário ao perfil Administrador da nova paróquia.
6. Grava `direitos_locais` com o `tenantId` gerado e `confirmadoEm = agora`.
7. Grava a resposta em `integracao_operacao`.

**Depois do commit**: envia o convite (ou o aviso de acesso). Falha de e-mail
não desfaz nada; fica pendente para "Reenviar convite".

Respostas:

| Caso | HTTP | Corpo |
|---|---|---|
| Criado agora | `201` | resposta abaixo |
| Mesma chave, mesmo corpo (repetição) | `200` + header `Idempotency-Replayed: true` | a resposta gravada, idêntica |
| Mesma chave, corpo diferente | `409 IDEMPOTENCY_KEY_CONFLITO` | erro |
| Slug usado, corrigido e reenviado com a **mesma** chave | `201` | a recusa `422` não é gravada; só a criação grava a operação |
| Slug usado por outra paróquia | `422 SLUG_EM_USO` | erro |
| Concorrente com a mesma chave | a segunda esbarra na restrição única, relê e devolve `200` com a resposta gravada | — |

```json
{
  "contratacaoId": "0f8e2c1a-1111-4a2b-9c3d-000000000001",
  "tenantId": "25c1d0e4-4444-4d5e-9f60-000000000004",
  "administrador": {
    "usuarioId": "7a8b9c0d-5555-4e6f-a071-000000000005",
    "situacao": "CONVIDADO"
  },
  "versaoDireitosAplicada": 1,
  "criadoEm": "2026-09-25T12:00:00Z"
}
```

`administrador.situacao`: `CONVIDADO` (usuário novo, convite enviado) ou
`VINCULADO` (e-mail já existia, só ganhou acesso).

**Idempotency-Key e edição (26/09/2026):** a chave é sempre o
`contratacaoId` do corpo — o app recusa outra. Por isso o emissor só pode
mudar nome, slug ou administrador antes de um envio que possa ter criado a
instância: antes do primeiro envio, ou depois de uma recusa 4xx que não seja
409. Depois de 2xx, 5xx, falha de rede ou 409, o corpo fica congelado.

### 5.2 Consultar instância (conciliação)

`GET /integracao/v1/instancias/{tenantId}` → `200`

```json
{
  "tenantId": "25c1d0e4-4444-4d5e-9f60-000000000004",
  "contratacaoId": "0f8e2c1a-1111-4a2b-9c3d-000000000001",
  "nome": "Paróquia São José",
  "slug": "sao-jose",
  "versaoDireitos": 18,
  "confirmadoEm": "2026-09-25T08:00:00Z",
  "acessoEfetivo": true,
  "uso": { "voluntarios": 37, "usuarios": 3, "armazenamento_mb": 120 }
}
```

`uso` é só contagem (nada de dado pessoal). Usado pela tela da Central e pelo
relatório de conciliação.

### 5.3 Webhook de direitos

`PUT /integracao/v1/instancias/{tenantId}/direitos` — corpo: `DireitosInstancia`.

| Caso | HTTP | Corpo |
|---|---|---|
| Versão maior que a local | `200` | `{"aplicado": true, "versaoAtual": 18}` |
| Versão menor ou igual | `200` | `{"aplicado": false, "versaoAtual": 18}` — atualiza `confirmadoEm` se igual; **não é erro**, a Central não repete |
| `tenantId` desconhecido | `404 INSTANCIA_NAO_ENCONTRADA` | erro |
| `contratacaoId` do corpo diferente do gravado | `409 IDEMPOTENCY_KEY_CONFLITO` | erro (alerta) |

**Outbox na Central**: o evento é gravado na mesma transação da alteração.
Repetições: 1 min, 5 min, 15 min, 1 h, 3 h e depois a cada 6 h até 72 h; aí
fica `FALHOU` com alerta (a sincronização de 8h cobre o intervalo). Só o
evento **mais recente** de cada contratação precisa ser entregue: ao enfileirar
uma versão nova, os pendentes mais antigos da mesma contratação são descartados.

### 5.4 Código de suporte

`POST /integracao/v1/instancias/{tenantId}/suporte`

```json
{
  "operador": { "id": "…", "nome": "Gustavo", "email": "…" },
  "motivo": "Cliente pediu ajuda para montar a escala de outubro"
}
```

→ `201`

```json
{
  "codigo": "Q3JZ-8KTM-2HX4",
  "urlAcesso": "https://app.servirea.com.br/suporte?codigo=Q3JZ-8KTM-2HX4",
  "expiraEm": "2026-09-25T12:02:00Z"
}
```

- Código de uso único, válido por 2 minutos, guardado como hash.
- O navegador do operador abre `urlAcesso`; o front chama
  `POST /auth/suporte/trocar {codigo}` (rota pública do app) e recebe o JWT de
  suporte **do próprio app** (curto, sem refresh).
- O app audita entrada e ações com o nome/e-mail do operador e o motivo.
- Aceita instância bloqueada.

### 5.5 Catálogo de recursos

`GET /integracao/v1/recursos` → `200` (26/09/2026)

```json
[
  { "codigo": "voluntarios", "nome": "Voluntários", "tipo": "LIMITE", "unidade": "pessoa", "aplicado": false },
  { "codigo": "ESCALAS", "nome": "Escalas", "tipo": "FUNCIONALIDADE", "unidade": null, "aplicado": false }
]
```

- Lista os códigos que o app entende em `limites` e `funcionalidades` (seção 4). A Central
  usa como sugestão ao cadastrar recursos, e o código do recurso fica **igual** ao do app
  (sem trocar maiúsculas/minúsculas).
- `aplicado`: se o app já faz valer o recurso. `false` = o código é aceito e guardado, mas
  ainda não limita nem esconde nada (aplicação futura).
- Sem instância: é do app inteiro. Mesma assinatura HMAC das outras rotas; corpo vazio.

## 6. App → Central

### 6.1 Sincronização de direitos

`GET /integracao/v1/produtos/{produto}/direitos?pagina=0&tamanho=100`

→ `200`

```json
{
  "itens": [ { "…": "DireitosInstancia" } ],
  "pagina": 0,
  "tamanho": 100,
  "total": 1,
  "geradoEm": "2026-09-25T16:00:00Z"
}
```

- Traz **todas** as contratações do produto que já têm `tenantId`
  (provisionadas), inclusive bloqueadas e canceladas.
- O app aplica as de versão maior e atualiza `confirmadoEm` de **todas** as
  presentes.
- Tenant que existe no app e não veio na lista → log de alerta (conciliação);
  **não** bloqueia sozinho.
- Quando: a cada 8 h e na subida da API. Falhou → tenta de novo a cada
  30 min até conseguir (o relógio das 72 h continua correndo).
- Parâmetro futuro (aditivo): `alteradosDesde` para buscar só o que mudou.

### 6.2 Erros de servidor (tela "Logs")

`POST /integracao/v1/produtos/{produto}/erros` → `202` (26/09/2026)

```json
{
  "erros": [
    {
      "id": "7b1c…", "ocorridoEm": "2026-09-26T18:00:00Z",
      "tenantId": "25c1d0e4-…", "usuarioId": "a3f0…",
      "metodo": "POST", "rota": "/pessoas", "status": 503, "codigo": null,
      "mensagem": "Storage não configurado. …", "requestId": "req-123"
    }
  ]
}
```

→ `{"recebidos": 1, "gravados": 1}`

- Só erros **5xx**. Erro 4xx (engano de quem digitou) não é enviado.
- **Nada de dado pessoal**: usuário só pelo id do app; `mensagem` só quando o texto é do próprio app
  (erro previsto). Em erro inesperado vai só o tipo (`"Erro inesperado (NullPointerException)"`): a
  mensagem técnica pode trazer valores do banco.
- `id` é do app: reenviar o mesmo lote não duplica (`gravados` 0). No máximo 100 por lote.
- O app junta em memória e manda a cada minuto; a Central fora do ar nunca atrasa a requisição da
  instância. A Central acha a contratação pelo `tenantId` e guarda 90 dias.

### 6.3 Minha conta (dados comerciais da instância)

`GET /integracao/v1/produtos/{produto}/instancias/{idExterno}/minha-conta` → `200` (01/10/2026)

Assinado como o resto do contrato (seção 2), corpo vazio. `idExterno` é o `tenantId` da instância. O app
chama quando a pessoa abre "Minha conta" (no Servirea, `GET /minha-conta`, só com `PERM_PAROQUIA`).

```json
{
  "cliente": {
    "nome": "Paróquia São José Operário",
    "documento": "12.345.678/0001-95",
    "endereco": {
      "logradouro": "Rua General Osório", "numero": "3191", "complemento": null,
      "bairro": "Centro", "cidade": "Cascavel", "uf": "PR", "cep": "85810-000"
    },
    "contatos": [ { "nome": "Maria", "email": "maria@…", "telefone": "(45) 99965-0660", "principal": true } ]
  },
  "contratacao": {
    "planoNome": "Plano Base", "periodicidade": "MENSAL", "valor": 150.00, "diaVencimento": 10,
    "inicio": "2026-09-01", "vigenteAte": null, "situacaoComercial": "ATIVA",
    "nomeInstancia": "São José Operário",
    "adicionais": [ { "nome": "Usuários extras", "quantidade": 3 } ]
  },
  "cobrancas": [
    {
      "id": "7b1c…", "competenciaInicio": "2026-10-01", "competenciaFim": "2026-10-31",
      "vencimento": "2026-10-10", "valor": 150.00, "situacao": "ABERTA", "vencida": true, "pagoEm": null
    }
  ]
}
```

- Só o que a paróquia pode ver. **Não** vão: motivo de isenção, `idempotencyKey`, direitos,
  dados de provisionamento (slug, admin), versões nem ids internos de cliente, produto e plano.
- Documento, CEP e telefone já vêm **formatados** (`Formatos`); o app mostra como veio.
- `situacao` da cobrança: `ABERTA`, `PAGA`, `CANCELADA` ou `ISENTA`. `vencida` = aberta com vencimento
  antes de hoje (fuso de Brasília). Cobranças só desta contratação, da mais recente para a mais antiga.
- Erros: `404` produto ou instância inexistente; `401` assinatura ausente ou inválida.
- No app (Servirea): `404` da Central → `404 CONTA_NAO_ENCONTRADA`; Central fora do ar, `5xx` ou
  assinatura recusada → `502 CENTRAL_INDISPONIVEL`; integração sem chave → `503 INTEGRACAO_NAO_CONFIGURADA`.

## 7. Tabelas de apoio no app (Servirea)

```
direitos_locais      tenant_id (PK, FK tenant), contratacao_id, versao, situacao,
                     acesso_liberado, motivo_bloqueio, vigente_ate, plano_codigo,
                     plano_nome, limites jsonb, funcionalidades text[],
                     confirmado_em, atualizado_em
integracao_operacao  idempotency_key (PK), tipo, hash_corpo, status_http,
                     resposta jsonb, criado_em
integracao_nonce     chave_id + nonce (PK), recebido_em
suporte_codigo       id, tenant_id, hash_codigo (único), operador_nome,
                     operador_email, motivo, expira_em, usado_em
```

Todas **globais** (sem `@TenantId`): são lidas pelo filtro de integração e pelo
Kill Switch fora do contexto de tenant. RLS ligado sem policy.

## 8. Checklist de implementação

- [ ] Assinador/validador HMAC com os vetores da seção 2.5 nos dois lados.
- [ ] Filtro de integração em cadeia própria (`/integracao/**`), sem JWT, sem CSRF, sem sessão.
- [ ] Nonce em transação própria; job de limpeza.
- [ ] `Idempotency-Key` obrigatória no provisionamento; hash do corpo; replay idêntico.
- [ ] Teste de concorrência: duas requisições simultâneas com a mesma chave → uma paróquia.
- [ ] Convite enviado só depois do commit; replay não reenvia.
- [ ] Webhook ignora versão antiga; atualiza `confirmadoEm` em versão igual.
- [ ] Sync de 8h + retry de 30 min; alerta às 24 h; bloqueio às 72 h; tolerância configurável.
- [ ] Outbox na Central com repetição e descarte de versões obsoletas.
- [ ] Código de suporte de uso único; troca por JWT do app; auditoria.
