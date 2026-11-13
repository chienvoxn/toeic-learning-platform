[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][ValidateSet('Backup','Restore','Verify')][string]$Action,
    [ValidateSet('toeic_learning_demo','toeic_admin06_test','toeic_admin06_regression')][string]$SourceDatabase='toeic_learning_demo',
    [string]$TargetDatabase='',
    [string]$Archive='',
    [ValidatePattern('^[A-Za-z_][A-Za-z0-9_]{0,62}$')][string]$User='q03_main_test',
    [string]$PostgresBin='C:\Program Files\PostgreSQL\17\bin',
    [string]$JdbcJar=''
)
$ErrorActionPreference='Stop'
$repoRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$backupRoot=Join-Path $repoRoot 'backups.local'
if($Action -ne 'Backup' -and ($TargetDatabase -notmatch '^toeic_admin06_restore_[a-z0-9_]{1,40}$' -or $TargetDatabase -eq $SourceDatabase)){throw 'Restore/Verify requires a separate ADMIN06 restore database'}
if(!(Test-Path -LiteralPath $backupRoot)){New-Item -ItemType Directory -Path $backupRoot | Out-Null}
if((Get-Item -LiteralPath $backupRoot).Attributes -band [IO.FileAttributes]::ReparsePoint){throw 'Backup directory must not be a link'}
if(!$Archive){if($Action -ne 'Backup'){throw 'Restore/Verify requires an archive path'};$Archive=Join-Path $backupRoot ($SourceDatabase+'_'+[DateTime]::UtcNow.ToString('yyyyMMdd_HHmmss')+'_'+[Guid]::NewGuid().ToString('N').Substring(0,8)+'.backup')}
$Archive=[IO.Path]::GetFullPath($Archive)
if([IO.Path]::GetDirectoryName($Archive) -ne $backupRoot){throw 'Archive must be directly inside repository backups.local'}
foreach($binary in @('pg_dump.exe','pg_restore.exe')){if(!(Test-Path -LiteralPath (Join-Path $PostgresBin $binary))){throw 'PostgreSQL tools are missing'}}
if(!$JdbcJar){
    $driverRoot=Join-Path ([Environment]::GetFolderPath('UserProfile')) '.m2/repository/org/postgresql/postgresql'
    $JdbcJar=Get-ChildItem -LiteralPath $driverRoot -Recurse -File -Filter 'postgresql-*.jar' | Where-Object {$_.Name -notmatch 'sources|javadoc'} | Sort-Object FullName -Descending | Select-Object -First 1 -ExpandProperty FullName
}
if(!$JdbcJar -or !(Test-Path -LiteralPath $JdbcJar)){throw 'Use the existing Maven PostgreSQL driver via -JdbcJar; no dependency is downloaded'}
$sourceFile=Join-Path $PSScriptRoot 'LocalDataManagement.java'
$targetArgument=if($TargetDatabase){$TargetDatabase}else{'-'}
& java --class-path $JdbcJar $sourceFile $Action $repoRoot $PostgresBin $User $SourceDatabase $targetArgument $Archive
if($LASTEXITCODE -ne 0){throw 'Local data drill failed; no success is claimed. Retain artifacts/target for inspection.'}
Write-Output ('Archive: '+$Archive)
