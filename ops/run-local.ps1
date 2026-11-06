#requires -Version 5.1
<#
.SYNOPSIS
    本地启动后端：从仓库外的 _local\hearth-db.env 读数据库凭据，注入环境变量后跑 Spring Boot。

.DESCRIPTION
    凭据不落在仓库里。这个脚本只是把 _local\hearth-db.env 的内容变成进程环境变量。

.EXAMPLE
    .\ops\run-local.ps1
    .\ops\run-local.ps1 -SkipBuild
#>
param(
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path $PSScriptRoot -Parent
$envFile  = Join-Path (Split-Path $repoRoot -Parent) '_local\hearth-db.env'

if (-not (Test-Path $envFile)) {
    throw "找不到凭据文件: $envFile`n先让 _local\db.env 填好，再由建库流程生成 hearth-db.env"
}

Get-Content $envFile -Encoding UTF8 | ForEach-Object {
    if ($_ -match '^\s*([A-Za-z_]+)\s*=\s*(.*)$') {
        $name = $matches[1]
        $value = $matches[2].Trim()
        Set-Item -Path "Env:$name" -Value $value
    }
}

Write-Host "凭据已注入（DB_NAME=$env:DB_NAME，DB_USER=$env:DB_USER，密码不回显）" -ForegroundColor Green
Write-Host "启动后端: http://127.0.0.1:8080/api/health" -ForegroundColor Cyan

$pom = Join-Path $repoRoot 'backend\pom.xml'

if ($SkipBuild) {
    & mvn -q -f $pom spring-boot:run
} else {
    & mvn -f $pom spring-boot:run
}

exit $LASTEXITCODE
