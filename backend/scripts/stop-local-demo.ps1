[CmdletBinding(SupportsShouldProcess)]
param([switch]$StopPostgres)
$ErrorActionPreference='Stop'
$repoRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$targetRoot=Join-Path $repoRoot 'backend/target'
$recordPath=Join-Path $targetRoot 'local-demo-processes.json'
if (!(Test-Path -LiteralPath $recordPath)) { throw 'No recorded demo processes; inspect ports manually. No process stopped.' }
$record=Get-Content -LiteralPath $recordPath -Raw | ConvertFrom-Json
$expectedCluster=[IO.Path]::GetFullPath((Join-Path $targetRoot 'q03-main-postgres'))
if ($record.database -ne 'toeic_learning_demo' -or [IO.Path]::GetFullPath($record.cluster) -ne $expectedCluster) { throw 'Unexpected demo record; no process stopped.' }
$processList=@(Get-CimInstance Win32_Process)
$runnerPattern=[regex]::Escape((Join-Path $targetRoot 'run-local-demo-backend.ps1'))
$expected=@{backend=$runnerPattern;web='node_modules[/\\]vite[/\\]bin[/\\]vite\.js';mail=[regex]::Escape((Join-Path $targetRoot 'web-auth-tools/mailpit.exe'))}
foreach ($service in @('web','backend','mail')) {
    $rootId=$record.$service
    if (!$rootId) { continue }
    $root=$processList | Where-Object ProcessId -eq $rootId
    if (!$root) { Write-Output "$service already stopped"; continue }
    if ($root.CommandLine -notmatch $expected[$service]) { throw "Recorded PID for $service now belongs to another command; stopping refused." }
    $tree=@($root)
    $frontier=@($root)
    while ($frontier.Count) {
        $parentIds=@($frontier.ProcessId)
        $frontier=@($processList | Where-Object { $_.ParentProcessId -in $parentIds -and $_.ProcessId -notin $tree.ProcessId })
        $tree+=$frontier
    }
    [array]::Reverse($tree)
    foreach ($entry in $tree) {
        if ($PSCmdlet.ShouldProcess("$service PID $($entry.ProcessId)",'Stop recorded demo process')) {
            $live=Get-CimInstance Win32_Process -Filter "ProcessId=$($entry.ProcessId)"
            if ($live -and $live.CreationDate -eq $entry.CreationDate) { Stop-Process -Id $entry.ProcessId -ErrorAction Stop }
        }
    }
}
if ($StopPostgres) {
    if (!$record.startedPostgres) { throw 'This demo did not start PostgreSQL; stop it through its original owner.' }
    $postgresBin='C:\Program Files\PostgreSQL\17\bin'
    $identity=& (Join-Path $postgresBin 'psql.exe') -X -w -h 127.0.0.1 -p 55459 -U q03_main_test -d postgres -Atc "SELECT current_setting('data_directory');"
    if ($LASTEXITCODE -ne 0 -or [IO.Path]::GetFullPath($identity) -ne $expectedCluster) { throw 'Unexpected PostgreSQL identity; stop refused.' }
    $others=& (Join-Path $postgresBin 'psql.exe') -X -w -h 127.0.0.1 -p 55459 -U q03_main_test -d postgres -Atc "SELECT count(*) FROM pg_stat_activity WHERE pid<>pg_backend_pid() AND backend_type='client backend';"
    if ($LASTEXITCODE -ne 0 -or $others -ne '0') { throw 'PostgreSQL has active clients; it will remain running.' }
    if ($PSCmdlet.ShouldProcess($expectedCluster,'Stop demo-started PostgreSQL without deleting data')) {
        & (Join-Path $postgresBin 'pg_ctl.exe') -D $expectedCluster -m fast -w -t 30 stop
        if ($LASTEXITCODE -ne 0) { throw 'PostgreSQL stop failed; inspect its log.' }
    }
}
