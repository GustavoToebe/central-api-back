# Ambiente local atual — 02/10/2026

Bases novas para testar o código atual, sem importar ou adaptar os cadastros antigos. Requisitos: Docker Desktop aberto, Java 21, Maven e Node com as dependências instaladas.

## Banco e APIs

No PowerShell, preparar o banco:

```powershell
& "C:/Users/ICONDESKTOP_02/Documents/central/central-api-back/scripts/ambiente-local-atual.ps1" -Aplicacao Banco
```

Em outro terminal, subir primeiro a Central e aguardar a inicialização:

```powershell
& "C:/Users/ICONDESKTOP_02/Documents/central/central-api-back/scripts/ambiente-local-atual.ps1" -Aplicacao Central
```

Em outro terminal, subir o Servirea:

```powershell
& "C:/Users/ICONDESKTOP_02/Documents/central/central-api-back/scripts/ambiente-local-atual.ps1" -Aplicacao Servirea
```

O script usa PostgreSQL 17 em ecossistema-db, porta 5433, bancos servirea_dev_atual e central_dev_atual. Instala stubs locais auth/storage; Flyway cria a estrutura vigente. Repetir os comandos preserva dados. Não apaga nem migra os bancos anteriores. Ignora application-dev-local.yml para evitar usar configuração real.

## Frontends

Em mais dois terminais:

```powershell
Set-Location "C:/Users/ICONDESKTOP_02/Documents/servire/servirea-api-front"
npm start -- --configuration local --port 4210 --host 127.0.0.1
```

```powershell
Set-Location "C:/Users/ICONDESKTOP_02/Documents/central/central-api-front"
npm start -- --configuration local --port 4211 --host 127.0.0.1
```

| Sistema | Navegador | API | Conta de exemplo |
|---|---|---|---|
| Servirea | http://localhost:4210 | http://localhost:8070 | paroquia@teste.local |
| Central | http://localhost:4211 | http://localhost:8071 | gustavo2@teste.local |

Senha das duas contas locais: **12345678**. Usar localhost no navegador, pois o CORS foi configurado para essa origem. Três coroinhas fictícios no Servirea, uma contratação e cobrança fictícias na Central. Credenciais e chaves exclusivamente locais; não são as contas reais da família.

As portas evitam os programas já em 8080/8081. APIs e fronts escutam nesta máquina. Encerrar com Ctrl+C nos terminais correspondentes. O banco permanece no volume Docker; não remover o volume se quiser guardar os testes.

## Limites

E-mail e WhatsApp usam log; Mercado Pago e Turnstile estão desconfigurados. Uploads que exigem Supabase Storage não ficam disponíveis só com PostgreSQL: os stubs são tabelas, não um servidor Storage. Sem deploy, cobranças ou envios reais. MFA requer chaves próprias quando instalado/configurado.

Os bancos antigos servire_dev e central_dev permaneceram guardados sem aplicação das migrations atuais. A lista real não foi importada para os novos bancos.

## Verificação desta preparação

Logins HTTP das duas APIs confirmados, health UP e fronts HTTP 200. Configuração local compilada e builds de produção aprovados. Testes: Servirea front 473 aprovados/1 ignorado preexistente; Central front 228 aprovados; AmbienteLocalTest da Central 4 aprovados, incluindo leitura do snapshot de direitos. A suíte completa dos backends não foi reexecutada nesta preparação. Inspeção visual manual continua pendente.

O script gera chaves MFA locais distintas fora do Git: USERPROFILE/.servirea-mfa-local.key e USERPROFILE/.central-mfa-local.key. Preservar esses arquivos; não são chaves de produção. O stub de e-mail não fornece links por log.
