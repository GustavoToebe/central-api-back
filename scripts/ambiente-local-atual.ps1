# Bases novas para testar o checkout atual. Não renomeia nem apaga as antigas.
param([ValidateSet('Banco','Servirea','Central')][string]$Aplicacao = 'Banco')
$ErrorActionPreference = 'Stop'
$centralRaiz = Split-Path $PSScriptRoot -Parent
$documentosRaiz = Split-Path (Split-Path $centralRaiz -Parent) -Parent
$servireaRaiz = Join-Path $documentosRaiz 'servire/servirea-api-back'
$containerLocal = 'ecossistema-db'
function Executar-Docker {
    param([string[]]$Argumentos)
    & docker @Argumentos
    if ($LASTEXITCODE -ne 0) { throw 'O comando Docker falhou. Confira se o Docker Desktop está aberto.' }
}
if ($Aplicacao -eq 'Banco') {
    $existe = Executar-Docker @('ps','-a','--filter',"name=^/$containerLocal$",'--format','{{.Names}}')
    if (-not $existe) {
        Executar-Docker @('run','--name',$containerLocal,'-e','POSTGRES_PASSWORD=postgres','-p','127.0.0.1:5433:5432','-d','postgres:17')
    } else { Executar-Docker @('start',$containerLocal) | Out-Null }
    $pronto = $false
    for ($tentativa=0; $tentativa -lt 30; $tentativa++) {
        & docker exec $containerLocal pg_isready -U postgres *> $null
        if ($LASTEXITCODE -eq 0) { $pronto=$true; break }
        Start-Sleep -Milliseconds 500
    }
    if (-not $pronto) { throw 'O PostgreSQL local não ficou pronto.' }
    $versao = Executar-Docker @('exec',$containerLocal,'psql','-U','postgres','-d','postgres','-Atc','SHOW server_version_num')
    if ([int]$versao -lt 170000 -or [int]$versao -ge 180000) { throw 'Este roteiro exige PostgreSQL 17.' }
    foreach ($bancoLocal in @('servirea_dev_atual','central_dev_atual')) {
        $achou = Executar-Docker @('exec',$containerLocal,'psql','-U','postgres','-d','postgres','-Atc',"SELECT 1 FROM pg_database WHERE datname='$bancoLocal'")
        if (-not $achou) { Executar-Docker @('exec',$containerLocal,'createdb','-U','postgres',$bancoLocal) }
    }
    $stub = Join-Path $servireaRaiz 'src/test/resources/testcontainers/supabase-stubs.sql'
    if (-not (Test-Path -LiteralPath $stub)) { throw "Stub local não encontrado: $stub" }
    $authExiste = Executar-Docker @('exec',$containerLocal,'psql','-U','postgres','-d','servirea_dev_atual','-Atc',"SELECT 1 FROM pg_namespace WHERE nspname='auth'")
    if (-not $authExiste) {
        Get-Content -LiteralPath $stub -Raw | & docker exec -i $containerLocal psql -U postgres -d servirea_dev_atual -v ON_ERROR_STOP=1 | Out-Null
        if ($LASTEXITCODE -ne 0) { throw 'Falha ao preparar os stubs do banco local.' }
    }
    Write-Host 'Bases locais atuais preparadas na porta 5433. Repetir este comando preserva os dados.'
    Write-Host 'Suba Servirea e Central em terminais separados com -Aplicacao Servirea/Central.'
    exit
}
# Credenciais públicas e exclusivamente locais; não reutilizar em produção.
$segredoLocal = 'c2VncmVkby1kZS10ZXN0ZS1uYW8tdXNhci1lbS1wcm9kdWNhby0wMTIzNDU2Nzg5'
$temporarioJava = Join-Path $env:USERPROFILE '.servirea-tmp'
New-Item -ItemType Directory -Path $temporarioJava -Force | Out-Null
$argumentosJvm = '-Dspring-boot.run.jvmArguments=-Djdk.net.unixdomain.tmpdir="' + $temporarioJava + '"'
# Chave aleatória persistida só no perfil local; perder o arquivo perde o MFA deste banco de teste.
$arquivoMfaLocal=Join-Path $env:USERPROFILE ('.' + $Aplicacao.ToLower() + '-mfa-local.key')
if (-not (Test-Path -LiteralPath $arquivoMfaLocal)) {
    $bytesMfaLocal=New-Object byte[] 32
    $geradorMfaLocal=[System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {$geradorMfaLocal.GetBytes($bytesMfaLocal)} finally {$geradorMfaLocal.Dispose()}
    [System.IO.File]::WriteAllText($arquivoMfaLocal,[Convert]::ToBase64String($bytesMfaLocal))
}
$chaveMfaLocal=[System.IO.File]::ReadAllText($arquivoMfaLocal).Trim()
if ([Convert]::FromBase64String($chaveMfaLocal).Length -ne 32) {throw 'Chave MFA local inválida.'}
$env:DB_USER='postgres'; $env:DB_PASSWORD='postgres' 
if ($Aplicacao -eq 'Servirea') {
    $env:DB_URL='jdbc:postgresql://localhost:5433/servirea_dev_atual'
    $env:SERVIRE_MFA_CHAVES="local:$chaveMfaLocal"; $env:SERVIRE_MFA_CHAVE_ATIVA='local'
    $env:PORT='8070'; $env:CORS_ALLOWED_ORIGINS='http://localhost:4210'
    $env:SERVIRE_INTEGRACAO_CHAVES_ENTRADA="teste-central:$segredoLocal"
    $env:SERVIRE_INTEGRACAO_CHAVE_SAIDA_ID='teste-servire'
    $env:SERVIRE_INTEGRACAO_CHAVE_SAIDA_SEGREDO=$segredoLocal
    $env:SERVIRE_INTEGRACAO_CENTRAL_URL='http://localhost:8071'
    Set-Location -LiteralPath $servireaRaiz
    & mvn spring-boot:run -DskipTests $argumentosJvm '-Dspring-boot.run.profiles=dev' '-Dspring-boot.run.arguments=--spring.config.import= --server.address=127.0.0.1 --spring.datasource.url=jdbc:postgresql://localhost:5433/servirea_dev_atual --servire.frontend.base-url=http://localhost:4210 --servire.email.provider=log --servire.whatsapp.provider=log --servire.storage.base-url= --servire.storage.service-role-key= --servire.turnstile.secret-key= --monitoramento.token='
} else {
    $env:DB_URL='jdbc:postgresql://localhost:5433/central_dev_atual'
    $env:CENTRAL_MFA_CHAVES="local:$chaveMfaLocal"; $env:CENTRAL_MFA_CHAVE_ATIVA='local'
    $env:PORT='8071'; $env:CORS_ALLOWED_ORIGINS='http://localhost:4211'
    $env:CENTRAL_JWT_SEGREDO=$segredoLocal
    $env:CENTRAL_OPERADOR_SEED_EMAIL='gustavo2@teste.local'; $env:CENTRAL_OPERADOR_SEED_SENHA='12345678'
    $env:CENTRAL_PRODUTO_SERVIRE_CHAVES_ENTRADA="teste-servire:$segredoLocal"
    $env:CENTRAL_PRODUTO_SERVIRE_CHAVE_SAIDA_ID='teste-central'
    $env:CENTRAL_PRODUTO_SERVIRE_CHAVE_SAIDA_SEGREDO=$segredoLocal
    Set-Location -LiteralPath $centralRaiz
    & mvn spring-boot:run -DskipTests $argumentosJvm '-Dspring-boot.run.profiles=dev' '-Dspring-boot.run.arguments=--spring.config.import= --server.address=127.0.0.1 --spring.datasource.url=jdbc:postgresql://localhost:5433/central_dev_atual --central.local.servirea-url=http://localhost:8070 --central.mercadopago.access-token= --central.mercadopago.webhook-secret= --monitoramento.token='
}
if ($LASTEXITCODE -ne 0) { throw 'A API local não iniciou corretamente. Consulte o erro acima.' }
