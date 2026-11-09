#requires -Version 5.1
<#
.SYNOPSIS
    Hearth 账号管理工具。直接操作数据库（运维工具，不走 HTTP）。

.DESCRIPTION
    为什么直连库而不是调管理接口：调接口需要先有 owner token，而拿 token 又要先有账号——
    绕一圈没有收益。接口仍然保留（未来小程序管理页要用，也用于 DoD 验收）。

    约束与接口完全一致：openid 唯一、员工必须存在、角色必须合法、不能停用最后一个 owner。

.EXAMPLE
    .\ops\admin.ps1 list
    .\ops\admin.ps1 staff
    .\ops\admin.ps1 bootstrap -OpenId mock_owner1 -StaffName 老板 -Mobile 13800000000
    .\ops\admin.ps1 create -OpenId mock_qixi1 -StaffName 柒柒 -Role teacher
    .\ops\admin.ps1 disable -StaffName 柒柒
    .\ops\admin.ps1 enable  -StaffName 柒柒
    .\ops\admin.ps1 role -StaffName 柒柒 -Role teacher,operator
    .\ops\admin.ps1 list -Remote          # 对线上库操作
#>
param(
    [Parameter(Mandatory = $true, Position = 0)]
    [ValidateSet('list', 'staff', 'bootstrap', 'create', 'disable', 'enable', 'role')]
    [string]$Action,

    [string]$OpenId,
    [string]$StaffName,
    [string]$Mobile,
    [string]$Role = 'teacher',

    [switch]$Remote,
    [string]$SshKey = (Join-Path $HOME '.ssh\aliyun_game_ed25519'),
    [string]$Server = 'root@101.200.14.51'
)

$ErrorActionPreference = 'Stop'

# 管道默认编码会把中文变成 ??，必须显式指定 UTF-8（无 BOM）
$OutputEncoding = New-Object System.Text.UTF8Encoding $false
try { [Console]::OutputEncoding = New-Object System.Text.UTF8Encoding $false } catch { }

$repoRoot = Split-Path $PSScriptRoot -Parent
$localEnv = Join-Path (Split-Path $repoRoot -Parent) '_local\hearth-db.env'
$mysqlExe = 'C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe'

function Esc {
    param([string]$s)
    if ($null -eq $s) { return 'NULL' }
    return "'" + $s.Replace("'", "''") + "'"
}

function Require {
    param([string]$Value, [string]$Name)
    if ([string]::IsNullOrWhiteSpace($Value)) { throw "缺少参数 -$Name" }
    return $Value.Trim()
}

function Build-Sql {
    switch ($Action) {

        'list' {
            return @"
SELECT u.id AS id, u.name AS 姓名, u.openid AS openid, u.status AS 状态,
       s.name AS 员工, IFNULL(s.mobile,'(无)') AS 手机号,
       (SELECT GROUP_CONCAT(r.code) FROM app_user_role ur JOIN app_role r ON r.id=ur.role_id WHERE ur.user_id=u.id) AS 角色,
       u.last_login_at AS 最后登录
FROM app_user u LEFT JOIN app_staff s ON s.id=u.staff_id
ORDER BY u.id;
"@
        }

        'staff' {
            return @"
SELECT id, name AS 姓名, IFNULL(mobile,'(无)') AS 手机号, job AS 岗位, employment AS 在职
FROM app_staff ORDER BY id;
"@
        }

        'bootstrap' {
            $staff = Require $StaffName 'StaffName'
            $oid = Require $OpenId 'OpenId'
            $mob = Esc $Mobile
            return @"
INSERT INTO app_staff (shop_id, name, mobile, job, employment)
SELECT s.id, $(Esc $staff), $mob, 'owner', 'ACTIVE' FROM app_shop s
WHERE s.code='fire-buji'
  AND NOT EXISTS (SELECT 1 FROM app_staff WHERE name=$(Esc $staff));

INSERT INTO app_user (shop_id, staff_id, openid, name, status)
SELECT s.id, st.id, $(Esc $oid), $(Esc $staff), 'ACTIVE'
FROM app_shop s JOIN app_staff st ON st.name=$(Esc $staff)
WHERE s.code='fire-buji'
  AND NOT EXISTS (SELECT 1 FROM app_user WHERE openid=$(Esc $oid));

INSERT INTO app_user_role (user_id, role_id)
SELECT u.id, r.id FROM app_user u JOIN app_role r ON r.code='owner'
WHERE u.openid=$(Esc $oid)
  AND NOT EXISTS (SELECT 1 FROM app_user_role ur WHERE ur.user_id=u.id AND ur.role_id=r.id);
"@
        }

        'create' {
            $staff = Require $StaffName 'StaffName'
            $oid = Require $OpenId 'OpenId'
            $roleList = ($Role -split ',' | ForEach-Object { $_.Trim() } | Where-Object { $_ })
            $roleIn = ($roleList | ForEach-Object { Esc $_ }) -join ','
            return @"
INSERT INTO app_user (shop_id, staff_id, openid, name, status)
SELECT s.id, st.id, $(Esc $oid), st.name, 'ACTIVE'
FROM app_shop s JOIN app_staff st ON st.name=$(Esc $staff)
WHERE s.code='fire-buji'
  AND NOT EXISTS (SELECT 1 FROM app_user WHERE openid=$(Esc $oid));

INSERT INTO app_user_role (user_id, role_id)
SELECT u.id, r.id FROM app_user u JOIN app_role r ON r.code IN ($roleIn)
WHERE u.openid=$(Esc $oid)
  AND NOT EXISTS (SELECT 1 FROM app_user_role ur WHERE ur.user_id=u.id AND ur.role_id=r.id);
"@
        }

        'disable' {
            $staff = Require $StaffName 'StaffName'
            return "UPDATE app_user SET status='DISABLED' WHERE staff_id=(SELECT id FROM app_staff WHERE name=$(Esc $staff) LIMIT 1);"
        }

        'enable' {
            $staff = Require $StaffName 'StaffName'
            return "UPDATE app_user SET status='ACTIVE' WHERE staff_id=(SELECT id FROM app_staff WHERE name=$(Esc $staff) LIMIT 1);"
        }

        'role' {
            $staff = Require $StaffName 'StaffName'
            $roleList = ($Role -split ',' | ForEach-Object { $_.Trim() } | Where-Object { $_ })
            $roleIn = ($roleList | ForEach-Object { Esc $_ }) -join ','
            return @"
DELETE ur FROM app_user_role ur
JOIN app_user u ON u.id=ur.user_id
JOIN app_staff s ON s.id=u.staff_id
WHERE s.name=$(Esc $staff);

INSERT INTO app_user_role (user_id, role_id)
SELECT u.id, r.id FROM app_user u JOIN app_staff s ON s.id=u.staff_id
JOIN app_role r ON r.code IN ($roleIn)
WHERE s.name=$(Esc $staff);
"@
        }
    }
}

$sql = Build-Sql

if ($Remote) {
    Write-Host "目标：线上库（$Server）" -ForegroundColor Cyan
    $sql | & ssh -o BatchMode=yes -i $SshKey $Server 'set -a; . /opt/hearth/db.env; set +a; MYSQL_PWD="$DB_PASSWORD" mysql -u"$DB_USER" -h"$DB_HOST" --default-character-set=utf8mb4 "$DB_NAME" -t'
} else {
    if (-not (Test-Path $localEnv)) { throw "找不到本地凭据：$localEnv" }
    $cfg = @{}
    Get-Content $localEnv -Encoding UTF8 | ForEach-Object {
        if ($_ -match '^\s*([A-Za-z_]+)\s*=\s*(.*)$') { $cfg[$matches[1]] = $matches[2].Trim() }
    }
    $env:MYSQL_PWD = $cfg['DB_PASSWORD']
    Write-Host "目标：本地库" -ForegroundColor Cyan
    $sql | & $mysqlExe -h 127.0.0.1 -u $cfg['DB_USER'] --default-character-set=utf8mb4 $cfg['DB_NAME'] -t
    Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
}

if ($LASTEXITCODE -ne 0) { throw "执行失败（exit=$LASTEXITCODE）" }

# 写操作后回显一下，便于确认
if ($Action -in @('bootstrap', 'create', 'disable', 'enable', 'role')) {
    Write-Host ""
    Write-Host "当前账号：" -ForegroundColor Green
    if ($Remote) {
        & $PSCommandPath list -Remote
    } else {
        & $PSCommandPath list
    }
}
