#requires -Version 5.1
<#
.SYNOPSIS
    Hearth 一键发布：构建 → 本地冒烟 → 上传 → 原子替换 → 三层验证 → 失败自动回滚。

.DESCRIPTION
    八步（对应 platform/docs/04-发布与运维.md）：
      1 预检    git 干净、mvn/java/ssh/密钥就绪
      2 构建     mvn clean package -DskipTests（release=21）
      3 本地冒烟  把新 jar 起在临时端口上真跑一遍 smoke.ps1
      4 上传    scp 到 /opt/hearth/app.jar.new（不覆盖正在跑的）
      5 切换    备份旧 jar → 原子 mv → systemctl restart
      6 三验    systemd active → 本机 8080 → 外网 /hearth/
      7 报告    写 ops/releases/<时间>-<版本>.md，并追加 CHANGELOG.md
      8 留底    保留 app.jar.prev 一个版本

    关键设计：
      - 第 1~4 步失败：线上纹丝不动，直接退出
      - 第 5~6 步失败：自动回滚到上一版并再次验证
      - 构建在本地，绝不在服务器（那台机器内存不够跑 Maven）

.PARAMETER SkipBuild
    跳过构建，直接上传现有 backend\target\hearth.jar（应急 / 重试上传时用）

.PARAMETER DryRun
    只打印将要做什么，不执行任何改动

.PARAMETER Force
    跳过「git 工作区必须干净」的预检

.PARAMETER Version
    指定版本号写入报告；默认读 pom.xml

.EXAMPLE
    .\ops\deploy.ps1
    .\ops\deploy.ps1 -DryRun
    .\ops\deploy.ps1 -SkipBuild -Force
#>
param(
    [switch]$SkipBuild,
    [switch]$DryRun,
    [switch]$Force,
    [string]$Version = '',
    [string]$SshKey = (Join-Path $HOME '.ssh\aliyun_game_ed25519'),
    [string]$Server = 'root@101.200.14.51',
    [string]$RemoteApp = '/opt/hearth/app.jar',
    [string]$Service = 'hearth',
    [int]$LocalPort = 18080
)

$ErrorActionPreference = 'Stop'

# ---------------------------------------------------------------- 路径
$repoRoot  = Split-Path $PSScriptRoot -Parent
$backend   = Join-Path $repoRoot 'backend'
$pom       = Join-Path $backend 'pom.xml'
$jar       = Join-Path $backend 'target\hearth.jar'
$smoke     = Join-Path $PSScriptRoot 'smoke.ps1'
$localDir  = Join-Path (Split-Path $repoRoot -Parent) '_local'
$localEnv  = Join-Path $localDir 'hearth-db.env'
$releaseDir = Join-Path $PSScriptRoot 'releases'
$changeLog = Join-Path $repoRoot 'CHANGELOG.md'

$mvn = 'E:\tools\maven\apache-maven-3.9.9\bin\mvn.cmd'

# 状态：用于失败时决定是否回滚
$script:swapped = $false
$script:reportLines = New-Object System.Collections.ArrayList

function Say {
    param([string]$Text, [string]$Color = 'Gray')
    Write-Host $Text -ForegroundColor $Color
}

function Step {
    param([string]$Name, [string]$Detail)
    Say ""
    Say "▶ $Name" Cyan
    if ($Detail) { Say "  $Detail" DarkGray }
    [void]$script:reportLines.Add("- **$Name** — $Detail")
}

function Fail {
    param([string]$Message)

    Say ""
    Say "✗ 失败：$Message" Red

    if ($script:swapped) {
        Say ""
        Say "检测到已切换过线上版本，开始自动回滚..." Yellow
        $rb = & ssh -o BatchMode=yes -i $SshKey $Server @"
set -e
if [ -f $RemoteApp.prev ]; then
  cp -f $RemoteApp.prev $RemoteApp
  systemctl restart $Service
  sleep 8
  systemctl is-active $Service
else
  echo 'NO_PREV'
fi
"@
        Say "回滚输出：$rb" Yellow
        $health = $null
        try {
            $health = Invoke-WebRequest -Uri 'https://www.huohuawd.cn/hearth/api/health' -UseBasicParsing -TimeoutSec 15
        } catch { }
        if ($null -ne $health -and $health.StatusCode -eq 200) {
            Say "已回滚，线上恢复可用" Yellow
            [void]$script:reportLines.Add("- **结果** — 失败并已自动回滚，线上已恢复")
        } else {
            Say "回滚后外网仍不可用，需要人工介入" Red
            [void]$script:reportLines.Add("- **结果** — 失败且回滚后仍不可用，需人工介入")
        }
    } else {
        [void]$script:reportLines.Add("- **结果** — 失败，线上未改动")
    }

    Write-Report $false $Message
    exit 1
}

function Write-Report {
    param([bool]$Ok, [string]$Note)

    if ($DryRun) { return }
    if (-not (Test-Path $releaseDir)) { New-Item -ItemType Directory -Path $releaseDir -Force | Out-Null }

    $stamp = Get-Date -Format 'yyyy-MM-dd-HHmm'
    if ($Ok) { $title = '发布成功' } else { $title = '发布失败' }
    $path = Join-Path $releaseDir "$stamp-$Version.md"

    $body = @()
    $body += "# $title · $Version"
    $body += ""
    $body += "- 时间：$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
    $body += "- 目标：$Server  →  https://www.huohuawd.cn/hearth/api/health"
    $body += "- 备注：$Note"
    $body += ""
    $body += "## 步骤"
    $body += ""
    $body += $script:reportLines
    [System.IO.File]::WriteAllText($path, ($body -join "`r`n"), (New-Object System.Text.UTF8Encoding $true))
    Say "报告已写入：$path" Gray

    if ($Ok) {
        $line = "- **$Version**（$(Get-Date -Format 'yyyy-MM-dd HH:mm')）发布成功"
        if (-not (Test-Path $changeLog)) {
            [System.IO.File]::WriteAllText($changeLog, "# CHANGELOG`r`n`r`n## 未发布`r`n`r`n", (New-Object System.Text.UTF8Encoding $true))
        }
        Add-Content -Path $changeLog -Value $line -Encoding UTF8
        Say "CHANGELOG 已追加" Gray
    }
}

# ---------------------------------------------------------------- 0 版本
if (-not $Version) {
    if (Test-Path $pom) {
        $m = Select-String -Path $pom -Pattern '<version>([^<]+)</version>' | Select-Object -First 2
        if ($m.Count -ge 2) { $Version = $m[1].Matches[0].Groups[1].Value }
        else { $Version = 'unknown' }
    } else { $Version = 'unknown' }
}

Say "==============================================" Cyan
Say " Hearth 发布  ·  $Version" Cyan
Say "==============================================" Cyan
if ($DryRun) { Say "  [DryRun] 只演练，不改动任何东西" Yellow }

# ---------------------------------------------------------------- 1 预检
Step '1 预检' 'git / mvn / java / ssh / 密钥'

if (-not (Test-Path $mvn)) { Fail "找不到 mvn：$mvn" }
if (-not (Test-Path $SshKey)) { Fail "找不到 SSH 密钥：$SshKey" }
if (-not (Get-Command java -ErrorAction SilentlyContinue)) { Fail 'PATH 里找不到 java' }
if (-not (Test-Path $smoke)) { Fail "找不到冒烟脚本：$smoke" }

Push-Location $repoRoot
try {
    $dirty = $null
    try { $dirty = & git status --porcelain } catch { $dirty = $null }

    if ($dirty -and -not $Force) {
        Pop-Location
        Say "工作区有未提交改动：" DarkGray
        $dirty | ForEach-Object { Say "  $_" DarkGray }
        Fail '工作区不干净（确认无误可加 -Force）'
    }

    # 取不到就降级，不要因为读不到 git 信息就中断发布
    $branch = 'unknown'
    $commit = 'unknown'
    try {
        $b = & git rev-parse --abbrev-ref HEAD
        if ($b) { $branch = "$b".Trim() }
        $c = & git rev-parse --short HEAD
        if ($c) { $commit = "$c".Trim() }
    } catch { }

    Say "  分支 $branch · 提交 $commit" Gray
    [void]$script:reportLines.Add("  - 分支 $branch · 提交 $commit")
} finally {
    if ((Get-Location).Path -eq $repoRoot) { Pop-Location }
}

# ---------------------------------------------------------------- 2 构建
if ($SkipBuild) {
    Step '2 构建' '已跳过（-SkipBuild）'
    if (-not (Test-Path $jar)) { Fail "没有可用的 jar：$jar" }
} else {
    Step '2 构建' 'mvn clean package -DskipTests（release=21）'
    if ($DryRun) {
        Say "  [DryRun] $mvn clean package" Yellow
    } else {
        & $mvn -B -q -f $pom clean package -DskipTests
        if ($LASTEXITCODE -ne 0) { Fail '构建失败' }
        if (-not (Test-Path $jar)) { Fail "构建完成但找不到 jar：$jar" }
        $sizeMb = [math]::Round((Get-Item $jar).Length / 1MB, 1)
        Say "  产物：$jar ($sizeMb MB)" Gray
        [void]$script:reportLines.Add("  - 产物 $sizeMb MB")
    }
}

# ---------------------------------------------------------------- 3 本地冒烟
Step '3 本地冒烟' "在新 jar 上起临时端口 $LocalPort 跑冒烟"

if ($DryRun) {
    Say "  [DryRun] 启动 jar 于 $LocalPort 并执行 smoke.ps1" Yellow
} else {
    if (-not (Test-Path $localEnv)) { Fail "找不到本地凭据：$localEnv" }

    $cfg = @{}
    Get-Content $localEnv -Encoding UTF8 | ForEach-Object {
        if ($_ -match '^\s*([A-Za-z_]+)\s*=\s*(.*)$') { $cfg[$matches[1]] = $matches[2].Trim() }
    }
    foreach ($k in $cfg.Keys) { Set-Item -Path "Env:$k" -Value $cfg[$k] }

    Say "  启动临时实例..." Gray
    $proc = Start-Process -FilePath 'java' -PassThru -WindowStyle Hidden `
        -ArgumentList @('-jar', $jar, "--server.port=$LocalPort")

    $ok = $false
    try {
        for ($i = 0; $i -lt 30; $i++) {
            Start-Sleep -Seconds 1
            try {
                $r = Invoke-WebRequest -Uri "http://127.0.0.1:$LocalPort/api/health" -UseBasicParsing -TimeoutSec 2
                if ($r.StatusCode -eq 200) { $ok = $true; break }
            } catch { }
        }
        if (-not $ok) { Fail "临时实例 30 秒内没有起来（端口 $LocalPort）" }

        & powershell -ExecutionPolicy Bypass -File $smoke -Env custom -BaseUrl "http://127.0.0.1:$LocalPort"
        if ($LASTEXITCODE -ne 0) { Fail '本地冒烟未通过' }
        [void]$script:reportLines.Add("  - 本地冒烟通过")
    } finally {
        if ($null -ne $proc -and -not $proc.HasExited) {
            Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue
            Say "  临时实例已停止" Gray
        }
    }
}

# ---------------------------------------------------------------- 4 上传
Step '4 上传' "scp → ${RemoteApp}.new"

if ($DryRun) {
    Say "  [DryRun] scp $jar → ${Server}:${RemoteApp}.new" Yellow
} else {
    & scp -i $SshKey -o BatchMode=yes -o ConnectTimeout=10 $jar "${Server}:${RemoteApp}.new"
    if ($LASTEXITCODE -ne 0) { Fail '上传失败' }
    Say "  上传完成（尚未影响线上）" Gray
    [void]$script:reportLines.Add("  - 上传完成")
}

# ---------------------------------------------------------------- 5 切换
Step '5 切换' '备份旧 jar → 原子 mv → 重启服务'

if ($DryRun) {
    Say "  [DryRun] cp app.jar app.jar.prev; mv app.jar.new app.jar; systemctl restart $Service" Yellow
} else {
    $out = & ssh -o BatchMode=yes -i $SshKey $Server @"
set -e
cp -f $RemoteApp ${RemoteApp}.prev
mv -f ${RemoteApp}.new $RemoteApp
chown hearth:hearth $RemoteApp
systemctl restart $Service
sleep 8
systemctl is-active $Service
"@
    $script:swapped = $true
    Say "  服务器：$out" Gray
    [void]$script:reportLines.Add("  - 切换完成：$out")
    if ("$out".Trim() -ne 'active') { Fail '服务切换后未处于 active' }
}

# ---------------------------------------------------------------- 6 三层验证
Step '6 三验' 'systemd → 本机 8080 → 外网 /hearth/'

if ($DryRun) {
    Say "  [DryRun] 三层验证" Yellow
} else {
    # 第一层
    $active = & ssh -o BatchMode=yes -i $SshKey $Server "systemctl is-active $Service"
    if ("$active".Trim() -ne 'active') { Fail "第一层失败：systemd 不是 active（$active）" }
    Say "  ① systemd: active" Green

    # 第二层
    $localHealth = & ssh -o BatchMode=yes -i $SshKey $Server 'curl -s --max-time 10 http://127.0.0.1:8080/api/health'
    if ("$localHealth" -notmatch '"ok":true') { Fail "第二层失败：本机 health 异常（$localHealth）" }
    Say "  ② 本机 8080: ok" Green

    # 第三层
    & powershell -ExecutionPolicy Bypass -File $smoke -Env prod
    if ($LASTEXITCODE -ne 0) { Fail '第三层失败：外网冒烟未通过' }
    Say "  ③ 外网 /hearth/: 冒烟通过" Green

    [void]$script:reportLines.Add("  - 三层验证全绿")
}

# ---------------------------------------------------------------- 7/8 报告
Say ""
Step '7 报告 / 8 留底' '写发布记录 + 保留 app.jar.prev'
Write-Report $true '全部步骤成功'

Say ""
Say "==============================================" Green
Say " 发布成功 · $Version" Green
Say " https://www.huohuawd.cn/hearth/api/health" Green
Say "==============================================" Green
exit 0
