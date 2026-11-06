#requires -Version 5.1
<#
.SYNOPSIS
    冒烟测试：发布前后各跑一次，确认「进程活着 + 数据库通着 + 鉴权生效」。

.DESCRIPTION
    检查三项：
      1. GET  /api/health          → 200，code=0，db.status=ok
      2. 返回里的库名是 hearth
      3. GET  /api/__smoke_401__   → 401，且 body 是统一 JSON（code=401）

    全部通过退出码 0，任一失败退出码 1（便于发布脚本在失败时中止）。

.EXAMPLE
    .\ops\smoke.ps1                                    # 本地 8080
    .\ops\smoke.ps1 -Env nginx                         # 本地 nginx 8081
    .\ops\smoke.ps1 -Env prod                          # 线上 /hearth/
    .\ops\smoke.ps1 -Env custom -BaseUrl http://192.168.1.8:8080
#>
param(
    [ValidateSet('local', 'nginx', 'prod', 'custom')]
    [string]$Env = 'local',

    [string]$BaseUrl = ''
)

$ErrorActionPreference = 'Stop'

if (-not $BaseUrl) {
    $BaseUrl = switch ($Env) {
        'local' { 'http://127.0.0.1:8080' }
        'nginx' { 'http://127.0.0.1:8081/hearth' }
        'prod'  { 'https://www.huohuawd.cn/hearth' }
        default { throw 'custom 环境必须提供 -BaseUrl' }
    }
}

function Invoke-Api {
    param([string]$Url)

    try {
        $r = Invoke-WebRequest -Uri $Url -Method Get -UseBasicParsing -TimeoutSec 10
        return @{ Status = [int]$r.StatusCode; Body = $r.Content }
    } catch {
        $resp = $_.Exception.Response
        $status = 0
        if ($null -ne $resp) { $status = [int]$resp.StatusCode }

        # PS 5.1：4xx/5xx 的响应体在 ErrorDetails.Message 里，比读流可靠
        $text = $_.ErrorDetails.Message
        if ([string]::IsNullOrEmpty($text) -and $null -ne $resp) {
            try {
                $reader = New-Object System.IO.StreamReader($resp.GetResponseStream())
                $text = $reader.ReadToEnd()
                $reader.Close()
            } catch {
                $text = ''
            }
        }
        if ([string]::IsNullOrEmpty($text)) { $text = $_.Exception.Message }

        return @{ Status = $status; Body = $text }
    }
}

$results = @()
function Add-Result {
    param([string]$Name, [bool]$Ok, [string]$Detail)
    $script:results += [pscustomobject]@{ Check = $Name; Ok = $Ok; Detail = $Detail }
}

Write-Host "冒烟目标: $BaseUrl" -ForegroundColor Cyan
Write-Host ''

# ---- 1. health 可用 ----
$health = Invoke-Api "$BaseUrl/api/health"
$body = $null
try { $body = $health.Body | ConvertFrom-Json } catch { }

$ok1 = ($health.Status -eq 200) -and ($null -ne $body) -and ($body.code -eq 0) -and ($body.data.ok -eq $true)
Add-Result 'health 返回 200 且 ok=true' $ok1 "HTTP=$($health.Status)"

$ok2 = ($null -ne $body) -and ($null -ne $body.data.db) -and ($body.data.db.status -eq 'ok') -and ($body.data.db.catalog -eq 'hearth')
$dbDetail = if ($null -ne $body) { "db=$($body.data.db.catalog)" } else { 'db=(无响应)' }
Add-Result '数据库连通且库名是 hearth' $ok2 $dbDetail

# ---- 2. 鉴权生效 ----
$unauth = Invoke-Api "$BaseUrl/api/__smoke_401__"
$ubody = $null
try { $ubody = $unauth.Body | ConvertFrom-Json } catch { }

$ok3 = ($unauth.Status -eq 401) -and ($null -ne $ubody) -and ($ubody.code -eq 401)
Add-Result '未授权接口返回 401 统一 JSON' $ok3 "HTTP=$($unauth.Status)"

# ---- 汇总 ----
$results | Format-Table -AutoSize

$failed = @($results | Where-Object { -not $_.Ok })
if ($failed.Count -gt 0) {
    Write-Host "冒烟失败：$($failed.Count) 项未通过" -ForegroundColor Red
    exit 1
}

Write-Host '冒烟通过' -ForegroundColor Green
exit 0
