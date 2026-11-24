[CmdletBinding()]
param([ValidateSet('Migrate','Validate')][string]$Action='Validate')
$ErrorActionPreference='Stop'
if (!$env:SPRING_DATASOURCE_URL) { throw 'Set SPRING_DATASOURCE_URL explicitly; the default local database is not selected by this script.' }
$backendRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
Push-Location $backendRoot
try {
    $mode=$Action.ToLowerInvariant()
    & .\mvnw.cmd spring-boot:run '-Dspring-boot.run.main-class=com.toeic.backend.system.infrastructure.migration.MigrationCli' "-Dspring-boot.run.arguments=--app.migrations.mode=$mode"
    if ($LASTEXITCODE -ne 0) { throw 'Migration coordinator failed; no repair, baseline or location fallback is attempted.' }
} finally { Pop-Location }
