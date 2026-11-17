param([switch]$BackendOnly)
$ErrorActionPreference='Stop'
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$backendRoot = Join-Path $repoRoot 'backend'
$targetRoot = Join-Path $backendRoot 'target'
$postgresBin = 'C:\Program Files\PostgreSQL\17\bin'
$clusterPath = Join-Path $targetRoot 'q03-main-postgres'
if (!(Test-Path -LiteralPath (Join-Path $clusterPath 'PG_VERSION'))) { throw 'Expected existing local PostgreSQL cluster; no personal DB will be used.' }
function Port-Open([int]$Port) {
    $client = [Net.Sockets.TcpClient]::new()
    try { return ($client.ConnectAsync('127.0.0.1',$Port).Wait(500) -and $client.Connected) } catch { return $false } finally { $client.Dispose() }
}
if (Port-Open 8080) { throw 'Port 8080 is occupied; inspect that service before starting another backend.' }
if (!$BackendOnly -and ((Port-Open 5173) -or (Port-Open 8025) -or (Port-Open 1025))) { throw 'A web/mail demo port is occupied; inspect the existing services first.' }
$startedPostgres = $false
if (!(Port-Open 55459)) {
    & (Join-Path $postgresBin 'pg_ctl.exe') -D $clusterPath -w -t 30 -o '-h 127.0.0.1 -p 55459 -c shared_buffers=32MB' start
    if ($LASTEXITCODE -ne 0) { throw 'Local PostgreSQL did not start' }
    $startedPostgres = $true
}
$psql = Join-Path $postgresBin 'psql.exe'
$identity = & $psql -X -w -h 127.0.0.1 -p 55459 -U q03_main_test -d postgres -Atc "SELECT inet_server_port(),current_setting('data_directory');"
if ($LASTEXITCODE -ne 0 -or $identity.Replace('\','/') -ne ('55459|'+$clusterPath.Replace('\','/'))) { throw 'Unexpected database server identity; refusing bootstrap' }
& $psql -X -w -h 127.0.0.1 -p 55459 -U q03_main_test -d postgres -v ON_ERROR_STOP=1 -q -c "DO `$`$ BEGIN IF NOT EXISTS(SELECT FROM pg_roles WHERE rolname='toeic_demo') THEN CREATE ROLE toeic_demo LOGIN; END IF; END `$`$;"
if ($LASTEXITCODE -ne 0) { throw 'Demo role bootstrap failed' }
$exists = & $psql -X -w -h 127.0.0.1 -p 55459 -U q03_main_test -d postgres -Atc "SELECT count(*) FROM pg_database WHERE datname='toeic_learning_demo';"
if ($exists -eq '0') {
    & (Join-Path $postgresBin 'createdb.exe') -h 127.0.0.1 -p 55459 -U q03_main_test -O toeic_demo toeic_learning_demo
    if ($LASTEXITCODE -ne 0) { throw 'Demo database creation failed; no reset attempted' }
}
$env:SPRING_PROFILES_ACTIVE='local-demo'
$env:DEBUG='false'
$env:MAVEN_OPTS='-Xms32m -Xmx192m -Xss512k -XX:ActiveProcessorCount=2 -XX:TieredStopAtLevel=1 -XX:ReservedCodeCacheSize=48m'
$backendRunner = Join-Path $targetRoot 'run-local-demo-backend.ps1'
@'
$ErrorActionPreference='Stop'
& .\mvnw.cmd -o spring-boot:run '-Dspring-boot.run.jvmArguments=-Xms64m -Xmx384m -Xss512k -XX:ActiveProcessorCount=2 -XX:TieredStopAtLevel=1 -XX:ReservedCodeCacheSize=64m' *> target/local-demo-backend.log
exit $LASTEXITCODE
'@ | Set-Content -LiteralPath $backendRunner -Encoding utf8
$backendProcess = Start-Process powershell.exe -ArgumentList '-NoProfile','-ExecutionPolicy','Bypass','-File',$backendRunner -WorkingDirectory $backendRoot -WindowStyle Hidden -PassThru
$processes = @{backend=$backendProcess.Id;startedPostgres=$startedPostgres;cluster=$clusterPath;database='toeic_learning_demo'}
if (!$BackendOnly) {
    $mailpit = Join-Path $targetRoot 'web-auth-tools/mailpit.exe'
    if (!(Test-Path -LiteralPath $mailpit)) { throw 'Install Mailpit locally or start backend with -BackendOnly; no download is performed.' }
    $mailProcess = Start-Process $mailpit -ArgumentList '--smtp','127.0.0.1:1025','--listen','127.0.0.1:8025','--disable-version-check' -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $targetRoot 'local-demo-mail.out.log') -RedirectStandardError (Join-Path $targetRoot 'local-demo-mail.err.log')
    $env:VITE_AUTH_MODE='remote'; $env:VITE_USE_MOCK='false'
    $webRoot = Join-Path $repoRoot 'frontend/web'
    $webProcess = Start-Process (Get-Command node).Source -ArgumentList 'node_modules/vite/bin/vite.js','--host','127.0.0.1','--strictPort' -WorkingDirectory $webRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $targetRoot 'local-demo-web.out.log') -RedirectStandardError (Join-Path $targetRoot 'local-demo-web.err.log')
    $processes.mail=$mailProcess.Id; $processes.web=$webProcess.Id
}
$processes | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $targetRoot 'local-demo-processes.json') -Encoding utf8
Write-Output 'Local demo starting: backend 8080, web 5173, Mailpit 8025; datasource toeic_learning_demo on 55459. No personal DB migration.'
