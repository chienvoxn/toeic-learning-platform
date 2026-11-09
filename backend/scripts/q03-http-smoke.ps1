param(
    [string]$BaseUrl='http://127.0.0.1:18080/api/v1',
    [string]$PsqlPath='C:\Program Files\PostgreSQL\17\bin\psql.exe',
    [string]$CryptoJar=(Join-Path $env:USERPROFILE '.m2/repository/org/springframework/security/spring-security-crypto/7.1.1/spring-security-crypto-7.1.1.jar')
)
# Run only against the dedicated local Q-03 HTTP database. No password/token output.
if (-not ([uri]$BaseUrl).IsLoopback) { throw 'Only loopback backend allowed' }
if (-not (Test-Path -LiteralPath $CryptoJar)) { throw 'Resolve Maven dependencies first or supply -CryptoJar' }
if (-not (Test-Path -LiteralPath 'src/test/resources/fixtures/q03-authoring.sql')) { throw 'Run this script from backend/' }
$ErrorActionPreference='Stop'
$q03Psql=$PsqlPath
$q03DbArgs=@('-h','127.0.0.1','-p','55459','-U','q03_main_test','-d','toeic_q03_http_test','-v','ON_ERROR_STOP=1')
$q03Identity= & $q03Psql @q03DbArgs -Atc 'select current_database(),current_user,inet_server_port();'
if ($q03Identity -ne 'toeic_q03_http_test|q03_main_test|55459') { throw 'Unexpected database; no fixture written' }
$q03Checks=[System.Collections.Generic.List[object]]::new()
$q03Base=$BaseUrl.TrimEnd('/')
function Invoke-Q03([string]$Method,[string]$Path,$Body,[string]$Token,[int]$Expected=200) {
    $q03Headers=@{}
    if ($Token) { $q03Headers.Authorization='Bearer '+$Token }
    $q03Options=@{Uri=($q03Base+$Path);Method=$Method;Headers=$q03Headers;UseBasicParsing=$true;TimeoutSec=15}
    if ($null -ne $Body) { $q03Options.ContentType='application/json';$q03Options.Body=($Body | ConvertTo-Json -Depth 12 -Compress) }
    $q03Response=$null
    try { $q03Response=Invoke-WebRequest @q03Options; $q03Status=[int]$q03Response.StatusCode }
    catch { if ($_.Exception.Response) { $q03Status=[int]$_.Exception.Response.StatusCode } else { throw ('HTTP connection failed at '+$Method+' '+$Path) } }
    if ($q03Status -ne $Expected) { throw ('Unexpected HTTP status '+$q03Status+' at '+$Method+' '+$Path) }
    $q03Checks.Add([pscustomobject]@{Method=$Method;Path=$Path;Status=$q03Status})
    if ($q03Response -and $q03Response.Content) { return ($q03Response.Content | ConvertFrom-Json) }
}
try {
    & $q03Psql @q03DbArgs -q -f 'src/test/resources/fixtures/q03-authoring.sql'
    if ($LASTEXITCODE -ne 0) { throw 'Fixture setup failed' }
    $q03Secret=[guid]::NewGuid().ToString('N')+'a1!'
    $q03Hash=$q03Secret | & java -Xmx64m -XX:ActiveProcessorCount=2 -XX:TieredStopAtLevel=1 --class-path $CryptoJar scripts/Q03FixtureHash.java
    if ($LASTEXITCODE -ne 0 -or -not $q03Hash.StartsWith('$2')) { throw 'Fixture hashing failed' }
    "UPDATE users SET password_hash='$q03Hash' WHERE user_id BETWEEN 'a3000000-0000-0000-0000-000000000001' AND 'a3000000-0000-0000-0000-000000000004';" | & $q03Psql @q03DbArgs -q
    if ($LASTEXITCODE -ne 0) { throw 'Fixture account setup failed' }
    $q03Author=(Invoke-Q03 POST '/auth/login' @{email='q03-author@example.invalid';password=$q03Secret} '').accessToken
    $q03Other=(Invoke-Q03 POST '/auth/login' @{email='q03-other@example.invalid';password=$q03Secret} '').accessToken
    $q03Admin=(Invoke-Q03 POST '/auth/login' @{email='q03-admin@example.invalid';password=$q03Secret} '').accessToken
    $q03Learner=(Invoke-Q03 POST '/auth/login' @{email='q03-learner@example.invalid';password=$q03Secret} '').accessToken
    Invoke-Q03 GET '/teacher/questions' $null '' 401 | Out-Null
    Invoke-Q03 GET '/teacher/questions' $null $q03Learner 403 | Out-Null
    $q03Group=Invoke-Q03 POST '/teacher/question-groups' @{groupType='PASSAGE';instruction='HTTP instruction';passageText='HTTP passage';assets=@(@{mediaId='b3000000-0000-0000-0000-000000000001';transcript='HTTP passage transcript'})} $q03Author 201
    $q03GroupId=$q03Group.group.groupId
    $q03GroupVersion=$q03Group.versions[0].groupVersionId
    $q03PartId=[int](& $q03Psql @q03DbArgs -Atc 'select part_id from toeic_parts where part_number=3;')
    $q03Choices=@()
    for($i=0;$i -lt 4;$i++) { $q03Choices+=@{label=([char](65+$i)).ToString();content=('HTTP choice '+$i);isCorrect=($i -eq 0);displayOrder=($i+1)} }
    $q03Question=Invoke-Q03 POST '/teacher/questions' @{partId=$q03PartId;groupVersionId=$q03GroupVersion;questionText='HTTP prompt';explanation='HTTP explanation';choices=$q03Choices;assets=@(@{mediaId='b3000000-0000-0000-0000-000000000006'})} $q03Author 201
    $q03QuestionId=$q03Question.question.questionId
    $q03Version=$q03Question.versions[0].questionVersionId
    if ($q03Question.versions[0].groupVersionId -ne $q03GroupVersion -or $q03Question.versions[0].choices.Count -ne 4) { throw 'Question persistence mismatch' }
    $q03List=Invoke-Q03 GET '/teacher/questions?size=1' $null $q03Author
    if ($q03List.totalElements -ne 1) { throw 'List/count mismatch' }
    Invoke-Q03 GET ('/teacher/questions/'+$q03QuestionId) $null $q03Author | Out-Null
    Invoke-Q03 GET ('/teacher/questions/'+$q03QuestionId+'/history') $null $q03Author | Out-Null
    Invoke-Q03 GET ('/teacher/questions/'+$q03QuestionId) $null $q03Other 404 | Out-Null
    Invoke-Q03 GET ('/teacher/questions/'+$q03QuestionId) $null $q03Admin | Out-Null
    $q03Patched=Invoke-Q03 PATCH ('/teacher/questions/'+$q03QuestionId) @{questionText='HTTP edited';explanation=$null;assets=@()} $q03Author
    if ($q03Patched.versions[0].questionVersionId -ne $q03Version -or $null -ne $q03Patched.versions[0].explanation) { throw 'Draft PATCH mismatch' }
    Invoke-Q03 PATCH ('/teacher/questions/'+$q03QuestionId) @{questionText='Must rollback';choices=@($q03Choices[0])} $q03Author 400 | Out-Null
    $q03After=Invoke-Q03 GET ('/teacher/questions/'+$q03QuestionId) $null $q03Author
    if ($q03After.versions[0].questionText -ne 'HTTP edited') { throw 'Rollback mismatch' }
    Invoke-Q03 PATCH ('/teacher/question-groups/'+$q03GroupId) @{passageText='HTTP edited passage';instruction=$null} $q03Author | Out-Null
    Invoke-Q03 GET ('/teacher/question-groups/'+$q03GroupId+'/history') $null $q03Author | Out-Null
    Invoke-Q03 DELETE ('/teacher/questions/'+$q03QuestionId) $null $q03Author 204 | Out-Null
    Invoke-Q03 DELETE ('/teacher/questions/'+$q03QuestionId) $null $q03Author 204 | Out-Null
    Invoke-Q03 GET ('/teacher/questions/'+$q03QuestionId+'/history') $null $q03Author | Out-Null
    Invoke-Q03 DELETE ('/teacher/question-groups/'+$q03GroupId) $null $q03Author 204 | Out-Null
    Invoke-Q03 DELETE ('/teacher/question-groups/'+$q03GroupId) $null $q03Author 204 | Out-Null
    Invoke-Q03 PATCH ('/teacher/question-groups/'+$q03GroupId) @{passageText='Blocked'} $q03Author 409 | Out-Null
    Invoke-Q03 POST '/auth/logout' $null $q03Author 204 | Out-Null
    foreach($check in $q03Checks) { Write-Output ('PASS '+$check.Status+' '+$check.Method+' '+$check.Path) }
    Write-Output ('HTTP_REAL_CHECKS_PASSED='+$q03Checks.Count)
} finally {
    & $q03Psql @q03DbArgs -q -f 'src/test/resources/fixtures/q03-authoring-cleanup.sql'
    $q03Secret=$null;$q03Hash=$null;$q03Author=$null;$q03Other=$null;$q03Admin=$null;$q03Learner=$null
}
