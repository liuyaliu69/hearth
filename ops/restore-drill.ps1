#requires -Version 5.1
<#
.SYNOPSIS
    恢复演练：把线上生产的备份恢复到本地临时库，验证它真的能用。

.DESCRIPTION
    为什么在本地演练而不是在生产上：
      hearth 账号只有 hearth.* 的权限，恢复不到别的库；
      而恢复回生产库是破坏性操作，演练不该碰生产。

    流程：
      1. 从服务器取最新备份（或指定文件）
      2. 解压
      3. 在本机建临时库 hearth_restore
      4. 导入
      5. 逐表对比「表名集合」与「每表行数」
      6. 删除临时库与临时文件

    设计教训（2026-11-06）：第一版用 `^\w+\s+\d+$` 解析 "BASE TABLE 11"，
    因为类型名含空格而全部匹配失败，两边都取到空值，于是「空 == 空」被判为一致，
    演练假绿。现在改为：**任何 SQL 失败都抛错中止**，且逐表比对而不是只比总数。

.EXAMPLE
    .\ops\restore-drill.ps1
    .\ops\restore-drill.ps1 -DumpPath .\tmp\hearth-2026-11-06-1824.sql.gz
#>
param(
    [string]$DumpPath = '',
    [string]$SshKey = (Join-Path $HOME '.ssh\aliyun_game_ed25519'),
    [string]$Server = 'root@101.200.14.51',
    [string]$RemoteDir = '/var/backups/hearth',
    [string]$LocalEnv = '',
    [string]$SourceDb = 'hearth',
    [string]$RestoreDb = 'hearth_restore'
)

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path $PSScriptRoot -Parent
$localDir = Join-Path (Split-Path $repoRoot -Parent) '_local'
if (-not $LocalEnv) { $LocalEnv = Join-Path $localDir 'db.env' }

$mysql = 'C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe'
if (-not (Test-Path $mysql)) { throw "找不到 mysql 客户端: $mysql" }

# ---- 1. 本地 root 凭据（仓库外）----
if (-not (Test-Path $LocalEnv)) { throw "找不到本地凭据: $LocalEnv" }
$rootCfg = @{}
Get-Content $LocalEnv -Encoding UTF8 | ForEach-Object {
    if ($_ -match '^\s*([A-Za-z_]+)\s*=\s*(.*)$') { $rootCfg[$matches[1]] = $matches[2].Trim() }
}
if (-not $rootCfg['MYSQL_PASSWORD']) { throw '本地 db.env 里 MYSQL_PASSWORD 为空' }
$dbUser = $rootCfg['MYSQL_USER']
$env:MYSQL_PWD = $rootCfg['MYSQL_PASSWORD']

function Invoke-Sql {
    param([string]$Database, [string]$Query)
    $cliArgs = @('-h', '127.0.0.1', '-u', $dbUser, '-N', '-B', '-e', $Query)
    if ($Database) { $cliArgs += @('-D', $Database) }
    $out = & $mysql @cliArgs
    if ($LASTEXITCODE -ne 0) { throw "SQL 失败: $Query" }
    return @($out)
}

# ---- 2. 取备份文件 ----
if (-not $DumpPath) {
    Write-Host '从服务器查找最新备份...' -ForegroundColor Cyan
    $latest = & ssh -o BatchMode=yes -i $SshKey $Server "ls -1t $RemoteDir/hearth-*.sql.gz | head -1"
    if (-not $latest) { throw '服务器上没有备份文件' }
    $latest = "$latest".Trim()
    $DumpPath = Join-Path $env:TEMP (Split-Path $latest -Leaf)
    Write-Host "下载 $latest ..." -ForegroundColor Cyan
    & scp -o BatchMode=yes -i $SshKey "${Server}:$latest" $DumpPath
    if ($LASTEXITCODE -ne 0) { throw '下载失败' }
}

if (-not (Test-Path $DumpPath)) { throw "备份文件不存在: $DumpPath" }
$kb = [math]::Round((Get-Item $DumpPath).Length / 1KB, 1)
Write-Host "备份文件: $DumpPath ($kb KB)" -ForegroundColor Green

# ---- 3. 解压 ----
$sqlPath = Join-Path $env:TEMP ([System.IO.Path]::GetFileNameWithoutExtension($DumpPath))
$inStream = [System.IO.File]::OpenRead($DumpPath)
try {
    $gzStream = New-Object System.IO.Compression.GzipStream($inStream, [System.IO.Compression.CompressionMode]::Decompress)
    $outStream = [System.IO.File]::Create($sqlPath)
    try { $gzStream.CopyTo($outStream) } finally { $outStream.Close(); $gzStream.Close() }
} finally { $inStream.Close() }
$sqlKb = [math]::Round((Get-Item $sqlPath).Length / 1KB, 1)
Write-Host "已解压: $sqlPath ($sqlKb KB)" -ForegroundColor Green

# ---- 4. 建临时库并导入 ----
Write-Host "重建临时库 $RestoreDb ..." -ForegroundColor Cyan
Invoke-Sql '' "DROP DATABASE IF EXISTS $RestoreDb" | Out-Null
Invoke-Sql '' "CREATE DATABASE $RestoreDb DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_0900_ai_ci" | Out-Null

Write-Host '导入中...' -ForegroundColor Cyan
Get-Content $sqlPath -Raw -Encoding UTF8 | & $mysql -h 127.0.0.1 -u $dbUser --default-character-set=utf8mb4 $RestoreDb
if ($LASTEXITCODE -ne 0) { throw "导入失败（临时库 $RestoreDb 已保留，便于排查）" }

# ---- 5. 对比：表名集合 + 逐表行数 ----
function Get-TableRows {
    param([string]$Db)

    $rows = @{}
    $names = Invoke-Sql '' "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA='$Db' AND TABLE_TYPE='BASE TABLE'"
    foreach ($n in $names) {
        $name = "$n".Trim()
        if (-not $name) { continue }
        # 表名都在命名规范内（字母数字下划线），可以不加反引号
        $c = Invoke-Sql $Db "SELECT COUNT(*) FROM $name"
        $rows[$name] = [int]("$c".Trim())
    }

    $viewNames = @()
    $views = Invoke-Sql '' "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA='$Db' AND TABLE_TYPE='VIEW'"
    foreach ($v in $views) {
        $vn = "$v".Trim()
        if ($vn) { $viewNames += $vn }
    }

    return @{ Tables = $rows; Views = $viewNames }
}

# 参照物 = 备份的来源（服务器），不是本地库。
# 本地 hearth 有 baseline 记录、生产没有，拿本地当标准会误判。
Write-Host '从服务器读取参照数据...' -ForegroundColor Cyan
$remoteStatsScript = Join-Path $PSScriptRoot 'server-db-stats.sh'
& scp -o BatchMode=yes -i $SshKey $remoteStatsScript "${Server}:/tmp/server-db-stats.sh" | Out-Null
if ($LASTEXITCODE -ne 0) { throw '上传统计脚本失败' }

$remoteOut = & ssh -o BatchMode=yes -i $SshKey $Server 'sed -i "s/\r$//" /tmp/server-db-stats.sh; bash /tmp/server-db-stats.sh'
if ($LASTEXITCODE -ne 0) { throw '服务器统计脚本执行失败' }

$srcTables = @{}
$srcViews = @()
foreach ($line in $remoteOut) {
    $l = "$line".Trim()
    if (-not $l) { continue }
    if ($l -match '^T\|(.+)\|(\d+)$') {
        $srcTables[$matches[1]] = [int]$matches[2]
    } elseif ($l -match '^V\|(.+)$') {
        $srcViews += $matches[1]
    }
}
$srcStats = @{ Tables = $srcTables; Views = $srcViews }

$dstStats = Get-TableRows $RestoreDb

if ($srcStats.Tables.Count -eq 0) { throw "源库 $SourceDb 里没有任何表，无从比对（先确认库名）" }
if ($dstStats.Tables.Count -eq 0) { throw "恢复库 $RestoreDb 里没有任何表，导入没成功" }

$checks = @()
function Add-Check {
    param([string]$Name, [bool]$Ok, [string]$Detail)
    $script:checks += [pscustomobject]@{ Check = $Name; Ok = $Ok; Detail = $Detail }
}

$tableCountOk = ($srcStats.Tables.Count -eq $dstStats.Tables.Count)
Add-Check '表数量一致' $tableCountOk ("源=$($srcStats.Tables.Count) 恢复=$($dstStats.Tables.Count)")

$missing = @($srcStats.Tables.Keys | Where-Object { -not $dstStats.Tables.ContainsKey($_) })
$extra = @($dstStats.Tables.Keys | Where-Object { -not $srcStats.Tables.ContainsKey($_) })
$namesOk = (($missing.Count -eq 0) -and ($extra.Count -eq 0))
$namesDetail = "缺=[" + ($missing -join ',') + "] 多=[" + ($extra -join ',') + "]"
Add-Check '表名集合一致' $namesOk $namesDetail

$viewOk = ($srcStats.Views.Count -eq $dstStats.Views.Count)
Add-Check '视图数量一致' $viewOk ("源=$($srcStats.Views.Count) 恢复=$($dstStats.Views.Count)")

$diffList = @()
foreach ($k in $srcStats.Tables.Keys) {
    if ($dstStats.Tables.ContainsKey($k)) {
        if ($srcStats.Tables[$k] -ne $dstStats.Tables[$k]) {
            $diffList += "$k(源$($srcStats.Tables[$k])/恢复$($dstStats.Tables[$k]))"
        }
    }
}
$rowsOk = ($diffList.Count -eq 0)
if ($rowsOk) { $rowsDetail = '全部一致' } else { $rowsDetail = $diffList -join ' ' }
Add-Check '逐表行数一致' $rowsOk $rowsDetail

$checks | Format-Table -AutoSize

$failed = @($checks | Where-Object { -not $_.Ok })
if ($failed.Count -gt 0) {
    Write-Host "演练失败：$($failed.Count) 项未通过。临时库 $RestoreDb 已保留便于排查。" -ForegroundColor Red
    Remove-Item $sqlPath -Force -ErrorAction SilentlyContinue
    exit 1
}

# ---- 6. 清理 ----
Invoke-Sql '' "DROP DATABASE IF EXISTS $RestoreDb" | Out-Null
Remove-Item $sqlPath -Force -ErrorAction SilentlyContinue
Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue

Write-Host '恢复演练通过：备份可用，临时库已清理' -ForegroundColor Green
exit 0
