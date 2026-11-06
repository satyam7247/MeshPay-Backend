param(
    [Parameter(Mandatory = $true)][string]$MySqlHost,
    [Parameter(Mandatory = $true)][int]$MySqlPort,
    [Parameter(Mandatory = $true)][string]$MySqlUser,
    [Parameter(Mandatory = $true)][string]$EmptyDatabase,
    [Parameter(Mandatory = $true)][string]$BaselineDatabase
)

$ErrorActionPreference = 'Stop'
if (-not $env:MYSQL_PWD) { throw 'Set MYSQL_PWD in the environment before running this script.' }
if (-not (Get-Command mysql -ErrorAction SilentlyContinue)) { throw 'MySQL 8.0 client (mysql) was not found on PATH.' }

$mysqlArgs = @('--protocol=tcp', "--host=$MySqlHost", "--port=$MySqlPort", "--user=$MySqlUser", '--default-character-set=utf8mb4')
$migrationDir = Join-Path $PSScriptRoot '..\src\main\resources\db\migration\mysql'
$migrationDir = [System.IO.Path]::GetFullPath($migrationDir)

function Invoke-MigrationFile([string]$Database, [string]$File) {
    $path = Join-Path $migrationDir $File
    Get-Content -Raw -LiteralPath $path | & mysql @mysqlArgs "--database=$Database"
    if ($LASTEXITCODE -ne 0) { throw "MySQL migration failed for $File." }
}

# EmptyDatabase must be an empty MySQL 8.0 schema.
foreach ($file in @('V1__legacy_ledger.sql', 'V2__application_security_and_account_tables.sql',
                    'V3__complete_app_lock_columns.sql', 'V4__account_privacy_and_device_columns.sql')) {
    Invoke-MigrationFile $EmptyDatabase $file
}

# BaselineDatabase must contain tables created by the pre-Flyway Hibernate ddl-auto=update version.
foreach ($file in @('V2__application_security_and_account_tables.sql',
                    'V3__complete_app_lock_columns.sql', 'V4__account_privacy_and_device_columns.sql')) {
    Invoke-MigrationFile $BaselineDatabase $file
}

Write-Output 'Migration SQL applied to both schemas. Start the app against each schema with SPRING_PROFILES_ACTIVE=prod to run Hibernate ddl-auto=validate.'
