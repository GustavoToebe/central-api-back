# Sobe o Postgres local dos dois sistemas. Não mexe no Supabase.
# As APIs, no profile dev, povoam a paróquia de teste na primeira subida.
$ErrorActionPreference = "Stop"
$nome = "ecossistema-db"

$existe = docker ps -a --filter "name=^/$nome$" --format "{{.Names}}"
if (-not $existe) {
    docker run --name $nome -e POSTGRES_PASSWORD=postgres -p 5433:5432 -d postgres:17
    Start-Sleep -Seconds 4
} else {
    docker start $nome | Out-Null
}

function Garantir-Banco($banco) {
    $achou = docker exec $nome psql -U postgres -tAc "SELECT 1 FROM pg_database WHERE datname='$banco'"
    if ($achou.Trim() -ne "1") {
        docker exec $nome createdb -U postgres $banco
    }
}
Garantir-Banco "servire_dev"
Garantir-Banco "central_dev"

$stubs = @(
    "$PSScriptRoot\..\..\..\servire\servirea-api-back\src\test\resources\testcontainers\supabase-stubs.sql",
    "$PSScriptRoot\..\..\..\servire\servire-api-back\src\test\resources\testcontainers\supabase-stubs.sql"
) | Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $stubs) {
    Write-Host "Não achei o supabase-stubs.sql do Servire. O Flyway do Servire precisa dele antes da primeira subida." -ForegroundColor Red
    exit 1
}
$auth = docker exec $nome psql -U postgres -d servire_dev -tAc "SELECT 1 FROM pg_namespace WHERE nspname='auth'"
if ($auth.Trim() -ne "1") {
    Get-Content $stubs | docker exec -i $nome psql -U postgres -d servire_dev | Out-Null
}

Write-Host ""
Write-Host "Postgres local no ar (porta 5433). Bases: servire_dev e central_dev." -ForegroundColor Green
Write-Host "Suba as duas APIs com profile dev (cada uma no seu terminal). Na primeira vez elas criam:" -ForegroundColor Green
Write-Host "  Central  http://localhost:4201  gustavo2@teste.local / 12345678"
Write-Host "  Servire  http://localhost:4200  paroquia@teste.local / 12345678  (paróquia paroquia-teste)"
Write-Host "O botão Acessar aplicativo ainda precisa das chaves de integração dos dois lados (README, passos 2 e 3)."
