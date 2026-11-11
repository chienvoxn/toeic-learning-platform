param(
    [string]$BaseUrl='http://127.0.0.1:18082/api/v1',
    [string]$PsqlPath='C:\Program Files\PostgreSQL\17\bin\psql.exe',
    [string]$CryptoJar=(Join-Path $env:USERPROFILE '.m2/repository/org/springframework/security/spring-security-crypto/7.1.1/spring-security-crypto-7.1.1.jar')
)
# Real HTTP and dedicated test DB only. Credentials/tokens remain in memory, never in output.
$ErrorActionPreference='Stop'
if(-not ([uri]$BaseUrl).IsLoopback -or ([uri]$BaseUrl).Port -ne 18082){throw 'Expected loopback PRAC-02 backend on port 18082'}
if(-not (Test-Path -LiteralPath 'src/test/resources/fixtures/prac01.sql')){throw 'Run from backend/'}
if(-not (Test-Path -LiteralPath $CryptoJar)){throw 'Supply an existing spring-security-crypto jar'}
$pracDb=@('-h','127.0.0.1','-p','55459','-U','q03_main_test','-d','toeic_prac02_http_test','-v','ON_ERROR_STOP=1')
$pracIdentity=& $PsqlPath @pracDb -Atc "select current_database(),current_user,inet_server_port(),current_setting('data_directory');"
if($LASTEXITCODE -ne 0 -or $pracIdentity -ne 'toeic_prac02_http_test|q03_main_test|55459|D:/toeic-learning-platform/backend/target/q03-main-postgres'){throw 'Unexpected DB identity; no fixture written'}
$pracExisting=& $PsqlPath @pracDb -Atc "select count(*) from users where user_id between 'a3000000-0000-0000-0000-000000000001' and 'a3000000-0000-0000-0000-000000000004' or user_id between 'a4000000-0000-0000-0000-000000000001' and 'a4000000-0000-0000-0000-000000000002';"
if($LASTEXITCODE -ne 0 -or $pracExisting -ne '0'){throw 'Fixture users already present; refusing to overwrite'}
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
function Assert-Safe($Session){
    $value=$Session|ConvertTo-Json -Depth 30 -Compress
    foreach($hidden in @('"isCorrect"','"explanation"','"correctChoiceId"','"transcript"','"answerKey"','"storageUrl"','"storage_url"','"url"','PRAC_SECRET','PRAC_PRIVATE','Q03_LEAK_STORAGE')){
        if($value.Contains($hidden)){throw 'Sensitive field present before result'}
    }
}
try{
    foreach($fixture in @('q03-authoring.sql','prac01.sql')){
        & $PsqlPath @pracDb -q -f ('src/test/resources/fixtures/'+$fixture)
        if($LASTEXITCODE -ne 0){throw 'Fixture setup failed'}
    }
    $pracSecret=[guid]::NewGuid().ToString('N')+'a1!'
    $pracTmp=Join-Path (Get-Location) 'target/prac02-jvm-tmp'
    New-Item -ItemType Directory -Path $pracTmp -Force | Out-Null
    $pracHash=$pracSecret | & java ("-Djava.io.tmpdir="+$pracTmp) -Xmx64m -XX:ActiveProcessorCount=2 -XX:TieredStopAtLevel=1 --class-path $CryptoJar scripts/Q03FixtureHash.java
    if($LASTEXITCODE -ne 0 -or -not $pracHash.StartsWith('$2')){throw 'Fixture hashing failed'}
    "UPDATE users SET password_hash='$pracHash' WHERE user_id BETWEEN 'a4000000-0000-0000-0000-000000000001' AND 'a4000000-0000-0000-0000-000000000002' OR user_id='a3000000-0000-0000-0000-000000000001';" | & $PsqlPath @pracDb -q
    if($LASTEXITCODE -ne 0){throw 'Fixture credentials setup failed'}
    $learner=(Invoke-Prac POST '/auth/login' @{email='practice-learner@example.invalid';password=$pracSecret} '').accessToken
    $other=(Invoke-Prac POST '/auth/login' @{email='practice-other@example.invalid';password=$pracSecret} '').accessToken
    $teacher=(Invoke-Prac POST '/auth/login' @{email='q03-author@example.invalid';password=$pracSecret} '').accessToken
    $session=Invoke-Prac POST '/attempts' @{sourceType='PRACTICE_SNAPSHOT';practice=@{partNumbers=@(1,2,5);questionCount=5}} $learner 201
    Assert-Safe $session
    $attempt=$session.attemptId
    $pracPersisted=& $PsqlPath @pracDb -Atc "select count(*) from attempts where attempt_id='$attempt' and user_id='a4000000-0000-0000-0000-000000000001';"
    if($pracPersisted -ne '1'){throw 'HTTP backend did not persist to expected test DB'}
    $q1=$session.questions|Where-Object questionVersionId -eq 'd4100000-0000-0000-0000-000000000001'
    $q2=$session.questions|Where-Object questionVersionId -eq 'd4100000-0000-0000-0000-000000000002'
    $q3=$session.questions|Where-Object questionVersionId -eq 'd4100000-0000-0000-0000-000000000003'
    $answerPath='/attempts/'+$attempt+'/answers/'
    Invoke-Prac PUT ($answerPath+$q1.questionVersionId) @{selectedChoiceId=($q1.choices|Where-Object label -eq 'B').choiceId} $learner | Out-Null
    Invoke-Prac PUT ($answerPath+$q1.questionVersionId) @{selectedChoiceId=($q1.choices|Where-Object label -eq 'A').choiceId} $learner | Out-Null
    Invoke-Prac PUT ($answerPath+$q2.questionVersionId) @{selectedChoiceId=($q2.choices|Where-Object label -eq 'A').choiceId} $learner | Out-Null
    Invoke-Prac PUT ($answerPath+$q2.questionVersionId) @{selectedChoiceId=$null} $learner | Out-Null
    Invoke-Prac PUT ($answerPath+$q3.questionVersionId) @{selectedChoiceId=($q3.choices|Where-Object label -eq 'B').choiceId} $learner | Out-Null
    $reloaded=Invoke-Prac GET ('/attempts/'+$attempt) $null $learner
    Assert-Safe $reloaded
    if($reloaded.answers.Count -ne 3){throw 'Reloaded selections mismatch'}
    Invoke-Prac GET ('/attempts/'+$attempt+'/result') $null $learner 409 | Out-Null
    Invoke-Prac POST ('/attempts/'+$attempt+'/submit') $null '' 401 | Out-Null
    Invoke-Prac POST ('/attempts/'+$attempt+'/submit') $null $teacher 403 | Out-Null
    Invoke-Prac POST ('/attempts/'+$attempt+'/submit') $null $other 404 | Out-Null
    $result=Invoke-Prac POST ('/attempts/'+$attempt+'/submit') $null $learner
    if($result.status -ne 'GRADED' -or $result.totalQuestions -ne 5 -or $result.correctCount -ne 1 -or $result.incorrectCount -ne 1 -or $result.unansweredCount -ne 3 -or $result.rawScore -ne 1 -or $result.accuracy -ne 20){throw 'Raw grading mismatch'}
    foreach($question in $result.questions){
        $prompt=$session.questions|Where-Object questionVersionId -eq $question.questionVersionId
        if($question.correctChoiceId -ne ($prompt.choices|Where-Object label -eq 'A').choiceId -or $question.explanation -ne 'PRAC_SECRET_EXPLANATION'){throw 'Pinned answer/explanation mismatch'}
    }
    $resultJson=$result|ConvertTo-Json -Depth 20 -Compress
    foreach($hidden in @('transcript','storageUrl','storage_url','PRAC_SECRET_TRANSCRIPT','Q03_LEAK_STORAGE','assets','passage')){if($resultJson.Contains($hidden)){throw 'Unexpected restricted metadata in result'}}
    $read=Invoke-Prac GET ('/attempts/'+$attempt+'/result') $null $learner
    $again=Invoke-Prac POST ('/attempts/'+$attempt+'/submit') $null $learner
    if($resultJson -ne ($read|ConvertTo-Json -Depth 20 -Compress) -or $resultJson -ne ($again|ConvertTo-Json -Depth 20 -Compress)){throw 'Result/replay changed'}
    Invoke-Prac PUT ($answerPath+$q1.questionVersionId) @{selectedChoiceId=$null} $learner 409 | Out-Null
    Invoke-Prac GET ('/attempts/'+$attempt+'/result') $null $other 404 | Out-Null
    & $PsqlPath @pracDb -q -c "UPDATE attempts SET started_at=now()-interval '25 hours',expires_at=now()-interval '1 hour' WHERE attempt_id='$attempt';"
    if($LASTEXITCODE -ne 0){throw 'Test expiry setup failed'}
    $expiredReplay=Invoke-Prac POST ('/attempts/'+$attempt+'/submit') $null $learner
    if($resultJson -ne ($expiredReplay|ConvertTo-Json -Depth 20 -Compress)){throw 'Expired replay changed'}
    $pracCounts=& $PsqlPath @pracDb -Atc "select (select count(*) from attempt_answers where attempt_id='$attempt'),(select count(*) from attempt_part_results where attempt_id='$attempt');"
    if($pracCounts -ne '5|3'){throw 'Duplicate or missing grading rows'}
    foreach($check in $pracChecks){Write-Output ('PASS '+$check.Status+' '+$check.Method+' '+$check.Path)}
    Write-Output ('HTTP_REAL_CHECKS_PASSED='+$pracChecks.Count)
}finally{
    foreach($fixture in @('prac01-cleanup.sql','q03-authoring-cleanup.sql')){
        & $PsqlPath @pracDb -q -f ('src/test/resources/fixtures/'+$fixture)
        if($LASTEXITCODE -ne 0){throw 'Fixture cleanup failed'}
    }
    $pracSecret=$null;$pracHash=$null;$learner=$null;$other=$null;$teacher=$null
}
