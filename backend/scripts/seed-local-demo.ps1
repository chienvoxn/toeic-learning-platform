param([string]$PsqlPath='C:\Program Files\PostgreSQL\17\bin\psql.exe')
$ErrorActionPreference='Stop'
$repoRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$backendRoot=Join-Path $repoRoot 'backend'
$credentialsPath=Join-Path $backendRoot 'target/local-demo-credentials.json'
$dbArgs=@('-X','-w','-h','127.0.0.1','-p','55459','-U','toeic_demo','-d','toeic_learning_demo','-v','ON_ERROR_STOP=1')
$identity=& $PsqlPath @dbArgs -Atc "SELECT current_database(),current_user,current_schema(),inet_server_port();"
if ($LASTEXITCODE -ne 0 -or $identity -ne 'toeic_learning_demo|toeic_demo|public|55459') { throw 'Unexpected DB identity; seed aborted' }
if (!(Test-Path -LiteralPath $credentialsPath)) {
    $secretBytes=New-Object byte[] 30
    $generator=[Security.Cryptography.RandomNumberGenerator]::Create()
    try { $generator.GetBytes($secretBytes) } finally { $generator.Dispose() }
    $secret=[Convert]::ToBase64String($secretBytes)+'aA9!'
    @{learnerEmail='learner@demo.example';teacherEmail='teacher@demo.example';adminEmail='admin@demo.example';password=$secret} | ConvertTo-Json | Set-Content -LiteralPath $credentialsPath -Encoding utf8
}
$credentials=Get-Content -LiteralPath $credentialsPath -Raw | ConvertFrom-Json
$cryptoJar=Join-Path $env:USERPROFILE '.m2/repository/org/springframework/security/spring-security-crypto/7.1.1/spring-security-crypto-7.1.1.jar'
Push-Location $backendRoot
try {
    $hash=$credentials.password | & java -Xmx64m -XX:ActiveProcessorCount=2 -XX:TieredStopAtLevel=1 --class-path $cryptoJar scripts/Q03FixtureHash.java
    if ($LASTEXITCODE -ne 0 -or !$hash.StartsWith('$2')) { throw 'Demo password hashing failed' }
    $sql="\set demo_password_hash '$hash'`n"+[IO.File]::ReadAllText((Join-Path $PSScriptRoot 'local-demo.sql'))
    $sql | & $PsqlPath @dbArgs -q
    if ($LASTEXITCODE -ne 0) { throw 'Demo seed failed; transaction rolled back' }
} finally { Pop-Location }
Write-Output 'Demo seed complete; rerun is idempotent. Login addresses/password are in backend/target/local-demo-credentials.json (ignored); no credential printed.'
