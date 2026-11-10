param(
    [string]$BaseUrl='http://127.0.0.1:18081/api/v1',
    [string]$PsqlPath='C:\Program Files\PostgreSQL\17\bin\psql.exe',
    [string]$CryptoJar=(Join-Path $env:USERPROFILE '.m2/repository/org/springframework/security/spring-security-crypto/7.1.1/spring-security-crypto-7.1.1.jar')
)
# Dedicated test DB only. Password/token live only in memory and are never printed.
$ErrorActionPreference='Stop'
if(-not ([uri]$BaseUrl).IsLoopback){throw 'Only loopback backend allowed'}
if(-not (Test-Path -LiteralPath 'src/test/resources/fixtures/prac01.sql')){throw 'Run from backend/'}
if(-not (Test-Path -LiteralPath $CryptoJar)){throw 'Resolve Maven dependencies or supply -CryptoJar'}
$pracDb=@('-h','127.0.0.1','-p','55459','-U','q03_main_test','-d','toeic_prac01_http_test','-v','ON_ERROR_STOP=1')
$pracIdentity=& $PsqlPath @pracDb -Atc 'select current_database(),current_user,inet_server_port();'
if($pracIdentity -ne 'toeic_prac01_http_test|q03_main_test|55459'){throw 'Unexpected test database; no fixture written'}
$pracChecks=[System.Collections.Generic.List[object]]::new()
function Invoke-Prac([string]$Method,[string]$Path,$Body,[string]$Token,[int]$Expected=200){
    $headers=@{};if($Token){$headers.Authorization='Bearer '+$Token}
    $options=@{Uri=($BaseUrl.TrimEnd('/')+$Path);Method=$Method;Headers=$headers;UseBasicParsing=$true;TimeoutSec=20}
    if($null -ne $Body){$options.ContentType='application/json';$options.Body=($Body|ConvertTo-Json -Depth 20 -Compress)}
    $response=$null
    try{$response=Invoke-WebRequest @options;$status=[int]$response.StatusCode}
    catch{if($_.Exception.Response){$status=[int]$_.Exception.Response.StatusCode}else{throw ('HTTP connection failed at '+$Method+' '+$Path)}}
    if($status -ne $Expected){throw ('Unexpected status '+$status+' at '+$Method+' '+$Path)}
    $pracChecks.Add([pscustomobject]@{Method=$Method;Path=$Path;Status=$status})
    if($response -and $response.Content){return ($response.Content|ConvertFrom-Json)}
}
function Start-Prac([int[]]$Parts,[int]$Count,[string]$Token,[int]$Expected=201){
    return Invoke-Prac POST '/attempts' @{sourceType='PRACTICE_SNAPSHOT';practice=@{partNumbers=@($Parts);questionCount=$Count}} $Token $Expected
}
function Assert-Safe($Session){
    $value=$Session|ConvertTo-Json -Depth 30 -Compress
    foreach($hidden in @('"isCorrect"','"explanation"','"transcript"','"answerKey"','"storageUrl"','"storage_url"','"url"','PRAC_SECRET','PRAC_PRIVATE','Q03_LEAK_STORAGE')){
        if($value.Contains($hidden)){throw 'Sensitive field present in practice JSON'}
    }
}
try{
    foreach($fixture in @('q03-authoring.sql','prac01.sql')){
        & $PsqlPath @pracDb -q -f ('src/test/resources/fixtures/'+$fixture)
        if($LASTEXITCODE -ne 0){throw 'Fixture setup failed'}
    }
    $pracSecret=[guid]::NewGuid().ToString('N')+'a1!'
    $pracTmp=Join-Path (Get-Location) 'target/prac01-jvm-tmp'
    New-Item -ItemType Directory -Path $pracTmp -Force | Out-Null
    $pracHash=$pracSecret | & java ("-Djava.io.tmpdir="+$pracTmp) -Xmx64m -XX:ActiveProcessorCount=2 -XX:TieredStopAtLevel=1 --class-path $CryptoJar scripts/Q03FixtureHash.java
    if($LASTEXITCODE -ne 0 -or -not $pracHash.StartsWith('$2')){throw 'Fixture hashing failed'}
    "UPDATE users SET password_hash='$pracHash' WHERE user_id BETWEEN 'a4000000-0000-0000-0000-000000000001' AND 'a4000000-0000-0000-0000-000000000002' OR user_id IN ('a3000000-0000-0000-0000-000000000001','a3000000-0000-0000-0000-000000000003');" | & $PsqlPath @pracDb -q
    if($LASTEXITCODE -ne 0){throw 'Fixture credentials setup failed'}
    $learner=(Invoke-Prac POST '/auth/login' @{email='practice-learner@example.invalid';password=$pracSecret} '').accessToken
    $other=(Invoke-Prac POST '/auth/login' @{email='practice-other@example.invalid';password=$pracSecret} '').accessToken
    $teacher=(Invoke-Prac POST '/auth/login' @{email='q03-author@example.invalid';password=$pracSecret} '').accessToken
    $admin=(Invoke-Prac POST '/auth/login' @{email='q03-admin@example.invalid';password=$pracSecret} '').accessToken
    Start-Prac @(1) 1 '' 401 | Out-Null
    Start-Prac @(1) 1 'bad-token' 401 | Out-Null
    Start-Prac @(1) 1 $teacher 403 | Out-Null
    Start-Prac @(1) 1 $admin 403 | Out-Null
    $multi=Start-Prac @(7,5,3,1,6,2,4) 13 $learner
    Assert-Safe $multi
    if($multi.totalQuestions -ne 13 -or $multi.expired){throw 'Multi-Part session mismatch'}
    if(([DateTimeOffset]::Parse($multi.expiresAt)-[DateTimeOffset]::Parse($multi.startedAt)).TotalHours -ne 24){throw 'Server expiry mismatch'}
    $reloaded=Invoke-Prac GET ('/attempts/'+$multi.attemptId) $null $learner
    if(($multi.questions|ConvertTo-Json -Depth 20 -Compress) -ne ($reloaded.questions|ConvertTo-Json -Depth 20 -Compress)){throw 'Snapshot changed on reload'}
    Invoke-Prac GET ('/attempts/'+$multi.attemptId) $null $other 404 | Out-Null
    Invoke-Prac GET '/attempts/00000000-0000-0000-0000-000000000001' $null $learner 404 | Out-Null
    Invoke-Prac GET '/questions' $null $learner 403 | Out-Null
    Start-Prac @(1,1) 2 $learner 400 | Out-Null
    Start-Prac @(1) 201 $learner 400 | Out-Null
    Start-Prac @(3) 5 $learner 409 | Out-Null
    $groups=Start-Prac @(3) 6 $learner
    $groupPins=@($groups.questions|ForEach-Object {$_.passage.groupVersionId}|Select-Object -Unique)
    if($groupPins.Count -ne 2 -or $groupPins -contains 'c4100000-0000-0000-0000-000000000001'){throw 'Non-greedy group selection failed'}
    & $PsqlPath @pracDb -q -c "UPDATE questions SET current_published_version_id=null WHERE question_id='d4000000-0000-0000-0000-000000000008';"
    Start-Prac @(3) 6 $learner 409 | Out-Null
    Invoke-Prac GET ('/attempts/'+$groups.attemptId) $null $learner | Out-Null
    & $PsqlPath @pracDb -q -c "UPDATE questions SET current_published_version_id='d4100000-0000-0000-0000-000000000008' WHERE question_id='d4000000-0000-0000-0000-000000000008';"
    Invoke-Prac POST '/attempts' @{sourceType='PRACTICE_SNAPSHOT';practice=@{partNumbers=@(5);questionCount=2;topicId=960001;difficulty='EASY'}} $learner 201 | Out-Null
    Invoke-Prac POST '/attempts' @{sourceType='PRACTICE_SNAPSHOT';practice=@{partNumbers=@(5);questionCount=1;topicId=999999;difficulty='EASY'}} $learner 409 | Out-Null
    $media=Start-Prac @(1) 2 $learner
    Assert-Safe $media
    if(-not (($media|ConvertTo-Json -Depth 20).Contains('b3000000-0000-0000-0000-000000000005'))){throw 'Authenticated media missing without authoring grant'}
    & $PsqlPath @pracDb -q -c "UPDATE media_resources SET visibility='PRIVATE' WHERE resource_id='b3000000-0000-0000-0000-000000000005';"
    $hidden=Invoke-Prac GET ('/attempts/'+$media.attemptId) $null $learner
    if(($hidden|ConvertTo-Json -Depth 20).Contains('b3000000-0000-0000-0000-000000000005')){throw 'Media visibility was not rechecked'}
    $pinned=Start-Prac @(4) 2 $learner
    $newGroup=[guid]::NewGuid().ToString()
    "INSERT INTO question_group_versions(group_version_id,group_id,version_number,status,instruction,passage_text,published_at,created_at) VALUES ('$newGroup','c4000000-0000-0000-0000-000000000004',2,'PUBLISHED','NEW','NEW',now(),now()); UPDATE question_groups SET current_published_version_id='$newGroup',status='ARCHIVED' WHERE group_id='c4000000-0000-0000-0000-000000000004'; UPDATE question_group_versions SET status='ARCHIVED',archived_at=now() WHERE group_version_id='c4100000-0000-0000-0000-000000000004'; UPDATE questions SET status='ARCHIVED' WHERE group_id='c4000000-0000-0000-0000-000000000004'; UPDATE question_versions SET status='ARCHIVED',archived_at=now() WHERE group_version_id='c4100000-0000-0000-0000-000000000004';" | & $PsqlPath @pracDb -q
    if($LASTEXITCODE -ne 0){throw 'Fixture archive failed'}
    $old=Invoke-Prac GET ('/attempts/'+$pinned.attemptId) $null $learner
    if(($old.questions|ConvertTo-Json -Depth 20 -Compress) -ne ($pinned.questions|ConvertTo-Json -Depth 20 -Compress)){throw 'Archive or republish changed pinned version'}
    Start-Prac @(4) 2 $learner 409 | Out-Null
    "UPDATE attempts SET started_at=now()-interval '25 hours',expires_at=now()-interval '1 hour' WHERE attempt_id='$($pinned.attemptId)';" | & $PsqlPath @pracDb -q
    $expired=Invoke-Prac GET ('/attempts/'+$pinned.attemptId) $null $learner
    if(-not $expired.expired -or $expired.questions.Count -ne 2){throw 'Expired snapshot unavailable'}
    & $PsqlPath @pracDb -q -c "DELETE FROM user_roles WHERE user_id='a4000000-0000-0000-0000-000000000001';"
    Invoke-Prac GET ('/attempts/'+$multi.attemptId) $null $learner 403 | Out-Null
    Invoke-Prac POST '/auth/logout' $null $learner 204 | Out-Null
    Invoke-Prac GET ('/attempts/'+$multi.attemptId) $null $learner 401 | Out-Null
    foreach($check in $pracChecks){Write-Output ('PASS '+$check.Status+' '+$check.Method+' '+$check.Path)}
    Write-Output ('HTTP_REAL_CHECKS_PASSED='+$pracChecks.Count)
}finally{
    foreach($fixture in @('prac01-cleanup.sql','q03-authoring-cleanup.sql')){& $PsqlPath @pracDb -q -f ('src/test/resources/fixtures/'+$fixture)}
    $pracSecret=$null;$pracHash=$null;$learner=$null;$other=$null;$teacher=$null;$admin=$null
}
