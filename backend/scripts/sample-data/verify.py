"""Verify sample SQL against the disposable toeic_sample_seed DB + backend :8086.

Never points at toeic_db. Credentials for disposable accounts stay in memory.
Run after starting the backend against 127.0.0.1:55439/toeic_sample_seed.
"""
import json
import os
import secrets
import subprocess
import urllib.error
import urllib.request
import uuid
from pathlib import Path

BASE = 'http://127.0.0.1:8086/api/v1'
PSQL = os.environ.get('PSQL_BIN', r'C:\Program Files\PostgreSQL\18\bin\psql.exe')
SQL = Path(__file__).resolve().parents[2] / 'sample-data' / 'toeic_sample_data.sql'
DATABASE = os.environ.get('SAMPLE_VERIFY_DATABASE', 'toeic_sample_seed')
if not DATABASE.startswith('toeic_sample_seed') or not DATABASE.replace('_', '').isalnum():
    raise ValueError('Only disposable toeic_sample_seed* verification databases are allowed.')
ARGS = [PSQL, '-w', '-h', '127.0.0.1', '-p', '55439', '-U', 'postgres', '-d', DATABASE, '-v', 'ON_ERROR_STOP=1']


def query(sql):
    return subprocess.check_output(ARGS + ['-At', '-c', sql], text=True).strip()


def seed():
    p = subprocess.run(ARGS + ['-f', str(SQL)], capture_output=True, text=True, encoding='utf-8')
    assert p.returncode == 0, p.stderr[-3000:]


def request(path, body=None, token=None, method=None, expected=200):
    headers = {'Content-Type': 'application/json'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    req = urllib.request.Request(BASE + path, headers=headers, method=method or ('POST' if body is not None else 'GET'),
                                 data=json.dumps(body).encode() if body is not None else None)
    try:
        with urllib.request.urlopen(req, timeout=60) as response:
            assert response.status == expected, (path, response.status)
            data = response.read()
            return json.loads(data) if data else None
    except urllib.error.HTTPError as e:
        code = json.loads(e.read()).get('code')
        raise AssertionError((path, e.code, code)) from None


def login(email, password):
    return request('/auth/login', {'email': email, 'password': password})['accessToken']


def counts():
    tables = ['tests', 'test_versions', 'test_questions', 'test_blueprints', 'test_blueprint_items',
              'questions', 'question_versions', 'question_choices', 'question_groups', 'question_group_versions',
              'media_resources', 'media_uploads', 'learning_contents', 'learning_content_versions',
              'vocabulary_decks', 'vocabularies', 'review_states', 'review_history', 'bookmarks',
              'learning_content_history', 'attempts', 'attempt_answers', 'attempt_question_snapshots',
              'attempt_part_results', 'learning_profiles', 'audit_logs', 'user_roles']
    return {t: int(query(f'SELECT count(*) FROM {t}')) for t in tables}


def main():
    assert query('SELECT count(*) FROM users') == '0', 'Use a new disposable verification DB.'
    accounts = {}
    for name in ['admin', 'teacher', 'learner']:
        password = secrets.token_urlsafe(20) + '!9aA'
        profile = request('/auth/register', {'email': f'{name}@toeic.local', 'password': password, 'fullName': f'Sample verifier {name}'}, expected=201)
        accounts[name] = dict(password=password, userId=profile['userId'])
    query("INSERT INTO learning_profiles(profile_id,user_id,target_score) VALUES('" + str(uuid.uuid4()) + "','" + accounts['learner']['userId'] + "',850)")
    seed()
    first_counts = counts()
    seed()
    assert counts() == first_counts, 'Second import must not duplicate data.'
    assert first_counts['questions'] == 200
    assert first_counts['media_uploads'] == 55
    assert first_counts['learning_contents'] == 12
    assert first_counts['vocabularies'] == 108
    assert first_counts['attempts'] == 12
    assert query("SELECT target_score FROM learning_profiles WHERE user_id='" + accounts['learner']['userId'] + "'") == '850'
    tokens = {name: login(f'{name}@toeic.local', a['password']) for name, a in accounts.items()}
    for name, token in tokens.items():
        profile = request('/users/me', token=token)
        assert ('ADMIN' if name == 'admin' else 'TEACHER' if name == 'teacher' else 'LEARNER') in profile['roles']
        overview = request('/learning-profile/overview', token=token)
        assert overview['totalVocabularies'] == 36
        assert overview['masteredVocabularies'] == 12
        assert overview['totalReviews'] == 84
        decks = request('/vocabulary-decks', token=token)
        assert len(decks) == 3
        assert all(len(request(f"/vocabulary-decks/{d['deckId']}/flashcards", token=token)) == 12 for d in decks)
        progress = request('/learning-profile/progress?days=7', token=token)
        assert sum(d['reviewCount'] for d in progress['dailyProgress']) > 0
        history = request('/users/me/exam-history', token=token)
        assert len(history) == 4
        assert len(request('/users/me/exam-progress', token=token)) == 7
        graded = [a for a in history if a['status'] == 'GRADED']
        assert len(graded) == 3
        for attempt in graded:
            result = request(f"/exam-attempts/{attempt['attempt_id']}/result", token=token)
            assert len(result['questions']) == result['totalQuestions']
            assert result['rawCorrect'] == sum(q['isCorrect'] for q in result['questions'])
            assert result['estimatedTotalScore'] is None
        resume = next(a for a in history if a['status'] == 'IN_PROGRESS')
        live = request(f"/exam-attempts/{resume['attempt_id']}", token=token)
        assert len(live['questions']) == 12
        assert sum(q['userAnswer'] is not None for q in live['questions']) == 3
        assert 'correctOption' not in json.dumps(live)
    teacher, learner = tokens['teacher'], tokens['learner']
    tests = request('/tests')
    assert sorted(t['totalQuestions'] for t in tests) == [12, 100, 100, 200]
    rows = request('/teacher/tests', token=teacher)
    assert len(rows) == 8
    assert {'DRAFT', 'IN_REVIEW', 'APPROVED'} <= {r['reviewStatus'] for r in rows}
    contents = request('/contents?size=100', token=learner)
    assert len(contents['content']) == 12
    assert len(request('/contents?size=100')['content']) == 8
    assert all(request('/contents/' + c['contentId'], token=learner)['body'] for c in contents['content'])
    assert request('/users/me/bookmarks?size=100', token=learner)['totalElements'] == 4
    assert request('/users/me/content-history?size=100', token=learner)['totalElements'] == 4
    # Real seeded media is available to a learner whose attempt pins it.
    full_result = next(a for a in request('/users/me/exam-history', token=learner) if a['total_questions'] == 200)
    result = request('/exam-attempts/' + full_result['attempt_id'] + '/result', token=learner)
    resource = result['questions'][0]['assets'][0]['resourceId']
    req = urllib.request.Request(BASE + '/media/' + resource, headers={'Authorization': 'Bearer ' + learner})
    with urllib.request.urlopen(req) as response:
        data = response.read()
        assert len(data) > 1000 and data[:4] in (b'RIFF', b'\x89PNG')
    # Preserve actual user work after subsequent imports.
    resume = next(a for a in request('/users/me/exam-history', token=learner) if a['status'] == 'IN_PROGRESS')
    live = request('/exam-attempts/' + resume['attempt_id'], token=learner)
    q = live['questions'][0]
    request('/exam-attempts/' + resume['attempt_id'] + '/answers', dict(questionId=q['id'], selectedOption=q['options'][0]['key'], clientSeq=100), token=learner, method='PUT')
    request('/vocabularies/' + request('/vocabulary-decks/' + request('/vocabulary-decks', token=learner)[0]['deckId'] + '/flashcards', token=learner)[0]['vocabularyId'] + '/reviews', {'rating': 3}, token=learner)
    before = query("SELECT md5(coalesce(string_agg(row_to_json(x)::text,'' ORDER BY attempt_answer_id),'')) FROM attempt_answers x")
    # Seed documents are accepted by the actual runtime validator, not just SQL constraints.
    for row in rows:
        detail = request('/teacher/tests/' + row['versionId'], token=teacher)
        assert request('/teacher/tests/validate', detail['document'], token=teacher) == []
    dynamic = request('/attempts', dict(sourceType='PRACTICE_SNAPSHOT', practice=dict(partNumbers=list(range(1, 8)), questionCount=20)), token=learner, expected=201)
    assert dynamic['totalQuestions'] == 20
    assert set(q['partNumber'] for q in dynamic['questions']) == set(range(1, 8))
    assert 'explanation' not in json.dumps(dynamic)
    # Admin can actually publish the seeded approved draft and Teacher can resume editing the draft.
    approved = next(r for r in rows if r['status'] == 'DRAFT' and r['reviewStatus'] == 'APPROVED')
    approved_detail = request('/teacher/tests/' + approved['versionId'], token=tokens['admin'])
    published = request('/teacher/tests/' + approved['versionId'] + '/publish', {'revision': approved_detail['revision']}, token=tokens['admin'])
    assert published['status'] == 'PUBLISHED'
    counts_after_work = counts()
    seed()
    assert counts() == counts_after_work
    assert query("SELECT md5(coalesce(string_agg(row_to_json(x)::text,'' ORDER BY attempt_answer_id),'')) FROM attempt_answers x") == before
    for token in tokens.values(): request('/auth/logout', token=token, method='POST', expected=204)
    print(json.dumps(dict(result='PASS',checks='SQL twice and after user activity; roles; 12 published lessons; bookmarks/history; 36 words and SRS per account; 200-question bank; 8 management states; 4 public tests; runtime document validation; dynamic practice all 7 Parts; Admin publication; graded results; media; resume; existing target preserved',counts=first_counts),ensure_ascii=False))


if __name__ == '__main__':main()
