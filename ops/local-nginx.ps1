#requires -Version 5.1
<#
.SYNOPSIS
    本地 nginx 控制：用仓库内配置跑你的 nginx.exe，不改动安装目录。

.DESCRIPTION
    nginx 支持 -p 指定前缀。本脚本把前缀指向 ops/nginx/，
    配置、日志、pid 全在仓库内，nginx 安装目录保持原样。

    ⚠️ 必须在真实终端（PowerShell 窗口 / Windows Terminal）里运行。
    从 GUI 宿主或无控制台的进程启动时，worker 会以 0xC0000142 退出
    —— 与 fireDance 发布说明记录的坑相同，不是配置问题。

.EXAMPLE
    .\ops\local-nginx.ps1 -Action start
#>
param(
    [ValidateSet('test', 'start', 'reload', 'stop', 'status')]
    [string]$Action = 'status',

    [string]$NginxExe = 'E:\tools\nginx-1.27.4\nginx-1.27.4\nginx.exe'
)

$ErrorActionPreference = 'Stop'

$prefix = Join-Path $PSScriptRoot 'nginx'
if (-not (Test-Path $prefix)) { throw "找不到前缀目录: $prefix" }
$prefixArg = $prefix.TrimEnd('\') + '\'

if (-not (Test-Path $NginxExe)) { throw "找不到 nginx.exe: $NginxExe" }

foreach ($d in 'logs', 'temp') {
    $p = Join-Path $prefix $d
    if (-not (Test-Path $p)) { New-Item -ItemType Directory -Path $p | Out-Null }
}

function Invoke-Nginx {
    param([string[]]$Args)
    & $NginxExe -p $prefixArg @Args
    if ($LASTEXITCODE -ne 0) { throw "nginx 执行失败（exit=$LASTEXITCODE）：$($Args -join ' ')" }
}

switch ($Action) {
    'test' {
        Invoke-Nginx @('-t', '-c', 'conf/nginx.conf')
        Write-Host '配置语法 OK' -ForegroundColor Green
    }
    'start' {
        $pidFile = Join-Path $prefix 'logs/nginx.pid'
        if (Test-Path $pidFile) {
            $oldPid = (Get-Content $pidFile -Raw).Trim()
            if (-not (Get-Process -Id $oldPid -ErrorAction SilentlyContinue)) {
                Remove-Item $pidFile -Force
                Write-Host '已清理残留 pid 文件' -ForegroundColor Yellow
            }
        }
        Invoke-Nginx @('-t', '-c', 'conf/nginx.conf')

        # nginx.exe 前台运行会阻塞 shell，必须用独立进程
        Start-Process -FilePath $NginxExe `
            -ArgumentList @('-p', $prefixArg, '-c', 'conf/nginx.conf') `
            -WorkingDirectory (Split-Path $NginxExe) `
            -WindowStyle Hidden | Out-Null

        Start-Sleep -Milliseconds 1200
        $c = Get-NetTCPConnection -LocalPort 8081 -State Listen -ErrorAction SilentlyContinue
        if ($c) {
            Write-Host "本地 nginx 已启动: http://localhost:8081/ping  (pid=$($c[0].OwningProcess))" -ForegroundColor Green
        } else {
            Write-Warning '8081 没监听，错误日志末尾：'
            $log = Join-Path $prefix 'logs/error.log'
            if (Test-Path $log) { Get-Content $log -Tail 5 }
        }
    }
    'reload' {
        Invoke-Nginx @('-t', '-c', 'conf/nginx.conf')
        Invoke-Nginx @('-s', 'reload', '-c', 'conf/nginx.conf')
        Write-Host '已 reload' -ForegroundColor Green
    }
    'stop' {
        try { Invoke-Nginx @('-s', 'stop', '-c', 'conf/nginx.conf') } catch { Write-Warning "停止时出错（可能本来没跑）: $_" }
        Write-Host '已停止'
    }
    'status' {
        $c = Get-NetTCPConnection -LocalPort 8081 -State Listen -ErrorAction SilentlyContinue
        if ($c) {
            Write-Host "运行中 (pid=$($c[0].OwningProcess))  http://localhost:8081/ping" -ForegroundColor Green
        } else {
            Write-Host '未运行' -ForegroundColor Yellow
        }
    }
}
