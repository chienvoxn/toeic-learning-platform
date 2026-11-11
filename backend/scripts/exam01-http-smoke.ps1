param(
    [string]$BaseUrl='http://127.0.0.1:18083/api/v1',
    [string]$PsqlPath='C:\Program Files\PostgreSQL\17\bin\psql.exe',
    [string]$CryptoJar=(Join-Path $env:USERPROFILE '.m2/repository/org/springframework/security/spring-security-crypto/7.1.1/spring-security-crypto-7.1.1.jar')
)
# Real HTTP and dedicated test DB only. Credentials/tokens remain in memory, never in output.
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
if(-not ([uri]$BaseUrl).IsLoopback -or ([uri]$BaseUrl).Port -ne 18083){throw 'Expected loopback EXAM-01 backend on port 18083'}
if(-not (Test-Path -LiteralPath 'src/test/resources/fixtures/prac01.sql')){throw 'Run from backend/'}
if(-not (Test-Path -LiteralPath $CryptoJar)){throw 'Supply an existing spring-security-crypto jar'}
$examDb=@('-w','-h','127.0.0.1','-p','55459','-U','q03_main_test','-d','toeic_exam01_http_test','-v','ON_ERROR_STOP=1')
$examIdentity=& $PsqlPath @examDb -Atc "select current_database(),current_user,inet_server_port(),current_setting('data_directory');"
if($LASTEXITCODE -ne 0 -or $examIdentity -ne 'toeic_exam01_http_test|q03_main_test|55459|D:/toeic-learning-platform/backend/target/q03-main-postgres'){throw 'Unexpected DB identity; no fixture written'}
$examExisting=& $PsqlPath @examDb -Atc "select count(*) from users where user_id between 'a3000000-0000-0000-0000-000000000001' and 'a3000000-0000-0000-0000-000000000004' or user_id between 'a4000000-0000-0000-0000-000000000001' and 'a4000000-0000-0000-0000-000000000002';"
if($LASTEXITCODE -ne 0 -or $examExisting -ne '0'){throw 'Fixture users already present; refusing to overwrite'}
$examChecks=[System.Collections.Generic.List[object]]::new()
function Invoke-Section([string]$Method,[string]$Path,$Body,[string]$Token,[int]$Expected=200){
    $headers=@{};if($Token){$headers.Authorization='Bearer '+$Token}
    $options=@{Uri=($BaseUrl.TrimEnd('/')+$Path);Method=$Method;Headers=$headers;UseBasicParsing=$true;TimeoutSec=20}
    if($null -ne $Body){$options.ContentType='application/json';$options.Body=($Body|ConvertTo-Json -Depth 20 -Compress)}
    $response=$null
    try{$response=Invoke-WebRequest @options;$status=[int]$response.StatusCode}
    catch{if($_.Exception.Response){$status=[int]$_.Exception.Response.StatusCode}else{throw ('HTTP connection failed at '+$Method+' '+$Path)}}
    if($status -ne $Expected){throw ('Unexpected status '+$status+' at '+$Method+' '+$Path)}
    $examChecks.Add([pscustomobject]@{Method=$Method;Path=$Path;Status=$status})
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
        & $PsqlPath @examDb -q -f ('src/test/resources/fixtures/'+$fixture)
        if($LASTEXITCODE -ne 0){throw 'Fixture setup failed'}
    }
    $examSecret=[guid]::NewGuid().ToString('N')+'a1!'
    $examTmp=Join-Path (Get-Location) 'target/prac02-jvm-tmp'
    New-Item -ItemType Directory -Path $examTmp -Force | Out-Null
    $examHash=$examSecret | & java ("-Djava.io.tmpdir="+$examTmp) -Xmx64m -XX:ActiveProcessorCount=2 -XX:TieredStopAtLevel=1 --class-path $CryptoJar scripts/Q03FixtureHash.java
    if($LASTEXITCODE -ne 0 -or -not $examHash.StartsWith('$2')){throw 'Fixture hashing failed'}
    "UPDATE users SET password_hash='$examHash' WHERE user_id BETWEEN 'a4000000-0000-0000-0000-000000000001' AND 'a4000000-0000-0000-0000-000000000002' OR user_id='a3000000-0000-0000-0000-000000000001';" | & $PsqlPath @examDb -q
    if($LASTEXITCODE -ne 0){throw 'Fixture credentials setup failed'}
    $learner=(Invoke-Section POST '/auth/login' @{email='practice-learner@example.invalid';password=$examSecret} '').accessToken
    $other=(Invoke-Section POST '/auth/login' @{email='practice-other@example.invalid';password=$examSecret} '').accessToken
    $teacher=(Invoke-Section POST '/auth/login' @{email='q03-author@example.invalid';password=$examSecret} '').accessToken
    Invoke-Section POST '/attempts' @{sourceType='SECTION_SNAPSHOT';section=@{type='FULL';questionCount=7;durationMinutes=1}} $learner 400 | Out-Null
    Invoke-Section POST '/attempts' @{sourceType='SECTION_SNAPSHOT';section=@{type='LISTENING';questionCount=1;durationMinutes=1}} $learner 409 | Out-Null
    Invoke-Section POST '/attempts' @{sourceType='SECTION_SNAPSHOT';section=@{type='READING';questionCount=6;durationMinutes=121}} $learner 400 | Out-Null
    foreach($type in @('LISTENING','READING')){
        $total=if($type -eq 'LISTENING'){7}else{6}
        $expectedParts=if($type -eq 'LISTENING'){@(1,2,3,4)}else{@(5,6,7)}
        $session=Invoke-Section POST '/attempts' @{sourceType='SECTION_SNAPSHOT';section=@{type=$type;questionCount=$total;durationMinutes=1}} $learner 201
        Assert-Safe $session
        $attempt=$session.attemptId
        if($session.totalQuestions -ne $total -or $session.section.type -ne $type -or $session.section.durationMinutes -ne 1){throw 'Section configuration mismatch'}
        if(([DateTimeOffset]::Parse($session.expiresAt)-[DateTimeOffset]::Parse($session.startedAt)).TotalSeconds -ne 60){throw 'Server deadline mismatch'}
        $parts=@($session.questions|ForEach-Object {$_.partNumber}|Sort-Object -Unique)
        if(@(Compare-Object $expectedParts $parts).Count -ne 0){throw 'Missing or out-of-section Part'}
        $examPersisted=& $PsqlPath @examDb -Atc "select count(*) from attempts where attempt_id='$attempt' and user_id='a4000000-0000-0000-0000-000000000001' and attempt_source_type='SECTION_SNAPSHOT' and section_type='$type' and configured_duration_seconds=60;"
        if($examPersisted -ne '1'){throw 'HTTP backend did not persist the expected test configuration'}
        $q1=$session.questions[0];$q2=$session.questions[1];$q3=$session.questions[2]
        $answerPath='/attempts/'+$attempt+'/answers/'
        Invoke-Section PUT ($answerPath+$q1.questionVersionId) @{selectedChoiceId=($q1.choices|Where-Object label -eq 'B').choiceId} $learner | Out-Null
        Invoke-Section PUT ($answerPath+$q1.questionVersionId) @{selectedChoiceId=($q1.choices|Where-Object label -eq 'A').choiceId} $learner | Out-Null
        Invoke-Section PUT ($answerPath+$q2.questionVersionId) @{selectedChoiceId=($q2.choices|Where-Object label -eq 'A').choiceId} $learner | Out-Null
        Invoke-Section PUT ($answerPath+$q2.questionVersionId) @{selectedChoiceId=$null} $learner | Out-Null
        Invoke-Section PUT ($answerPath+$q3.questionVersionId) @{selectedChoiceId=($q3.choices|Where-Object label -eq 'B').choiceId} $learner | Out-Null
        if($type -eq 'READING'){
            & $PsqlPath @examDb -q -c "UPDATE question_groups SET status='ARCHIVED' WHERE group_id IN ('c4000000-0000-0000-0000-000000000005','c4000000-0000-0000-0000-000000000006');"
            if($LASTEXITCODE -ne 0){throw 'Test source archive setup failed'}
        }
        $reloaded=Invoke-Section GET ('/attempts/'+$attempt) $null $learner
        Assert-Safe $reloaded
        if(($session.questions|ConvertTo-Json -Depth 20 -Compress) -ne ($reloaded.questions|ConvertTo-Json -Depth 20 -Compress)){throw 'Pinned questions changed on reload/archive'}
        if($reloaded.answers.Count -ne 3){throw 'Saved selections were not resumed'}
        Invoke-Section GET ('/attempts/'+$attempt+'/result') $null $learner 409 | Out-Null
        Invoke-Section POST ('/attempts/'+$attempt+'/submit') $null '' 401 | Out-Null
        Invoke-Section POST ('/attempts/'+$attempt+'/submit') $null $teacher 403 | Out-Null
        Invoke-Section POST ('/attempts/'+$attempt+'/submit') $null $other 404 | Out-Null
        if($type -eq 'READING'){
            & $PsqlPath @examDb -q -c "UPDATE attempts SET started_at=now()-interval '2 minutes',expires_at=now()-interval '1 minute' WHERE attempt_id='$attempt';"
            if($LASTEXITCODE -ne 0){throw 'Test expiry setup failed'}
            Invoke-Section PUT ($answerPath+$q1.questionVersionId) @{selectedChoiceId=$null} $learner 409 | Out-Null
            $expired=Invoke-Section GET ('/attempts/'+$attempt) $null $learner
            Assert-Safe $expired
            if(-not $expired.expired -or $expired.status -ne 'IN_PROGRESS'){throw 'Expired Section should await explicit submission'}
        }
        $result=Invoke-Section POST ('/attempts/'+$attempt+'/submit') $null $learner
        if($result.status -ne 'GRADED' -or $result.totalQuestions -ne $total -or $result.correctCount -ne 1 -or $result.incorrectCount -ne 1 -or $result.unansweredCount -ne ($total-2) -or $result.rawScore -ne 1 -or $result.accuracy -ne [math]::Round(100.0/$total,2)){throw 'Section grading mismatch'}
        if(@($result.parts).Count -ne $expectedParts.Count -or ($result.parts|Measure-Object totalQuestions -Sum).Sum -ne $total -or ($result.parts|Measure-Object correctCount -Sum).Sum -ne 1){throw 'Persisted Part results mismatch'}
        foreach($question in $result.questions){
            $prompt=$session.questions|Where-Object questionVersionId -eq $question.questionVersionId
            if($question.correctChoiceId -ne ($prompt.choices|Where-Object label -eq 'A').choiceId -or $question.explanation -ne 'PRAC_SECRET_EXPLANATION'){throw 'Pinned marking key/explanation mismatch'}
        }
        $resultJson=$result|ConvertTo-Json -Depth 20 -Compress
        foreach($hidden in @('transcript','storageUrl','storage_url','PRAC_SECRET_TRANSCRIPT','Q03_LEAK_STORAGE','assets','passage','estimatedScore','totalScore')){if($resultJson.Contains($hidden)){throw 'Restricted metadata or TOEIC conversion leaked'}}
        $read=Invoke-Section GET ('/attempts/'+$attempt+'/result') $null $learner
        $again=Invoke-Section POST ('/attempts/'+$attempt+'/submit') $null $learner
        if($resultJson -ne ($read|ConvertTo-Json -Depth 20 -Compress) -or $resultJson -ne ($again|ConvertTo-Json -Depth 20 -Compress)){throw 'Section replay changed'}
        Invoke-Section PUT ($answerPath+$q1.questionVersionId) @{selectedChoiceId=$null} $learner 409 | Out-Null
        Invoke-Section GET ('/attempts/'+$attempt+'/result') $null $other 404 | Out-Null
        $examCounts=& $PsqlPath @examDb -Atc "select (select count(*) from attempt_answers where attempt_id='$attempt'),(select count(*) from attempt_part_results where attempt_id='$attempt');"
        if($examCounts -ne ($total.ToString()+'|'+$expectedParts.Count)){throw 'Duplicate or missing Section result snapshots'}
    }
    foreach($check in $examChecks){Write-Output ('PASS '+$check.Status+' '+$check.Method+' '+$check.Path)}
    Write-Output ('HTTP_SECTION_CHECKS_PASSED='+$examChecks.Count)
}finally{
    foreach($fixture in @('prac01-cleanup.sql','q03-authoring-cleanup.sql')){
        & $PsqlPath @examDb -q -f ('src/test/resources/fixtures/'+$fixture)
        if($LASTEXITCODE -ne 0){throw 'Fixture cleanup failed'}
    }
    $examSecret=$null;$examHash=$null;$learner=$null;$other=$null;$teacher=$null
}
