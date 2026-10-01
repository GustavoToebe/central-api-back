# Regera o schema.sql a partir do Postgres local (ecossistema-db, porta 5433).
# Pré-requisito: o container no ar e a API em profile dev já ter subido uma vez com as migrations novas (Flyway).
# Uso, na pasta deste repositório: .\scripts\gerar-schema.ps1
param([string]$Container = "ecossistema-db", [string]$Banco = "central_dev", [string]$Usuario = "postgres")
$ErrorActionPreference = "Stop"
$destino = Join-Path $PSScriptRoot "..\schema.sql"
$cabecalho = @(
    "-- Esquema do banco, gerado das migrations (V001-V012). NÃO editar à mão.",
    "-- Para regerar: scripts/gerar-schema.ps1 (precisa do Postgres local com a API em dev já ter subido).",
    "-- Só o schema public, sem dono e sem permissões. O banco de produção é criado pelo Flyway a partir destas migrations.",
    ""
)
$dump = docker exec $Container pg_dump -U $Usuario -d $Banco --schema=public --schema-only --no-owner --no-privileges |
    Where-Object { $_ -notmatch '^(SET |SELECT pg_catalog\.set_config|-- Dumped |-- PostgreSQL database dump)' }
if ($LASTEXITCODE -ne 0) { throw "Falha ao gerar o schema; arquivo anterior preservado." }
$utf8 = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllLines($destino, ($cabecalho + $dump), $utf8)
Write-Host "Gerado $destino" -ForegroundColor Green
Write-Host "Atualize o número da última migration no cabeçalho e no SCHEMA.md se entrou migration nova."
