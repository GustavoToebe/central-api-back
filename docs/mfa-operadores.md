# MFA dos operadores da Central

Implementação local na branch de melhorias, migration V013. Não confirma implantação.
Este recurso é da Central; MFA dos usuários do Servirea continua pendente.

## Uso e contrato

No Meu perfil, informar a senha atual, preparar o autenticador e cadastrar a chave
manualmente em um aplicativo compatível com TOTP. Confirmar seis dígitos em até
dez minutos. Preparar novamente substitui a preparação anterior; não ativa MFA.
Nome da conta: Central. Algoritmo SHA-1, seis dígitos, período de 30 segundos.
Usar o identificador da própria conta no aplicativo para distinguir operadores.

- `GET /operadores/eu/mfa`: ativo, configurado e quantidade de códigos restantes; nunca segredo.
- `POST /operadores/eu/mfa/preparar`: `{senha}`; devolve `{segredo, expiraEm}`.
- `POST /operadores/eu/mfa/ativar`: `{senha, codigo}`; confirma a preparação, devolve dez códigos de recuperação uma única vez.
- `POST /operadores/eu/mfa/desativar`: `{senha, codigo}`; exige TOTP novo ou recuperação de uso único; 204.
- `POST /auth/login`: campos existentes e `codigoMfa` opcional. Senha correta sem segundo fator de conta protegida recebe 401 `MFA_NECESSARIO`, sem emitir sessão/cookie. Senha incorreta continua recebendo a recusa genérica.

Ativar/desativar incrementa a versão das credenciais e apaga todos os refresh
tokens na mesma transação. Bearers anteriores passam a ser recusados. Tokens
legados sem versão são aceitos somente enquanto a versão do operador for zero.
Depois de guardar os códigos, sair e entrar novamente; aguardar o próximo código
do autenticador, pois o usado na ativação já foi consumido.

Cada recuperação tem 128 bits aleatórios, em 32 caracteres hexadecimais minúsculos.
Guarda-se apenas SHA-256; consumo é atualização condicional atômica. Recuperação
exige também a senha e permite entrar, sem desativar MFA automaticamente. Para
desativar depois, usar outro código ainda disponível. Não há reexibição dos
códigos nem endpoint de redefinição por e-mail que contorne o segundo fator.

Todas as alterações exigem ROLE_OPERADOR. Login e ações de configuração usam
os [limites de tentativas](login-limites.md), incluindo Retry-After. Esses limites
são por instância, ainda sem coordenação entre réplicas. Erros `MFA_INVALIDO` e
`MFA_SENHA_INVALIDA` não provocam refresh/repetição automática no front.

## Segurança e concorrência

Segredo TOTP de 160 bits, gerado por SecureRandom; janela de um passo anterior e
um posterior. Persistir o último passo aceito impede reutilizar código ou voltar
a passos anteriores. Relógio do servidor precisa estar sincronizado.

Login, preparação, ativação, desativação, troca de senha e refresh adquirem
`FOR NO KEY UPDATE` no operador. Isso serializa consumo de fatores e mudanças
de credenciais, permitindo a FK da auditoria em REQUIRES_NEW. Refresh consulta
primeiro apenas o id do operador e relê o token depois da trava; uma ativação
concorrente não deixa um refresh antigo criar sessão posterior à revogação.

Segredo ativo e pendente cifrados com AES-256-GCM, nonce aleatório de 12 bytes,
AAD com id do operador. Falha de cifra/chave é 503 `MFA_INDISPONIVEL`, sem
liberar login somente com senha. Respostas de configuração têm Cache-Control
no-store. Auditoria registra ações, sem senha, chave TOTP ou recuperação.
O front guarda material de configuração apenas na memória do componente,
limpa ao concluir/destruir e não usa serviço externo de QR code.

## Configuração e operação

Variáveis próprias, diferentes das chaves JWT/HMAC:

- `CENTRAL_MFA_CHAVES`: lista `id:base64`, separada por vírgula; cada chave tem exatamente 32 bytes.
- `CENTRAL_MFA_CHAVE_ATIVA`: id presente na lista. Sem ambas, login sem MFA permanece disponível, mas preparação é recusada com 503.

Gerar chaves com gerador criptográfico e cadastrar no ambiente seguro da API.
Não inserir valores reais no Git, chats ou registros de execução. Fazer backup
das chaves em cofre separado do backup do banco, com acesso restrito. Perder as
chaves torna segredos cifrados inutilizáveis. A API valida formato/duplicação/id
ativo ao iniciar; não há verificação antecipada de todos os segredos existentes.

Rotação: manter as chaves antigas no keyring e selecionar uma nova ativa; novas
preparações usam a nova. **Recifragem dos registros antigos ainda não está
implementada. Não remover chaves antigas** usadas por registros ou backups.
Deixar a API sem as chaves de registros existentes não desativa o MFA.

Se operador perder autenticador e todos os códigos, não há recuperação automática.
Exige procedimento administrativo de identidade verificada e acesso restrito ao
banco, com registro de incidente, invalidação de sessões e nova configuração.
Esse procedimento operacional ainda precisa ser ensaiado; não é uma rota pública.
MFA é opt-in; política de obrigatoriedade, passkeys e recuperação assistida ficam
para outro bloco. Não foi alterado banco ou ambiente de produção nesta fase.

## Evidências

Testes de vetores SHA-1 do [RFC 6238](https://www.rfc-editor.org/rfc/rfc6238),
janela/replay, cifra com AAD, adulteração, keyring, ativação/expiração via HTTP,
revogação de access/refresh, recuperação de uso único e consumo concorrente.
Referência de desenho: [OWASP MFA](https://cheatsheetseries.owasp.org/cheatsheets/Multifactor_Authentication_Cheat_Sheet.html).
