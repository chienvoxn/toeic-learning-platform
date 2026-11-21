#!/usr/bin/env python3
"""Stdlib end-to-end smoke: run AFTER docker compose up --build -d."""
import json
import os
import time
import uuid
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

BASE = os.getenv("GATEWAY_URL", "http://localhost:8088").rstrip("/")


def req(method, path, payload=None, token=None, expected=(200,)):
    data = json.dumps(payload).encode() if payload is not None else None
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    request = Request(BASE + path, data=data, headers=headers, method=method)
    try:
        with urlopen(request, timeout=15) as response:
            status = response.status
            body = response.read().decode()
    except HTTPError as error:
        status = error.code
        body = error.read().decode()
    except URLError as error:
        raise SystemExit(f"Gateway not reachable: {error}") from error
    if status not in expected:
        raise AssertionError(f"{method} {path} expected {expected}, got {status}: {body[:300]}")
    return status, json.loads(body) if body and body.lstrip().startswith(("{", "[")) else body


def run():
    email = f"exam-demo-{uuid.uuid4().hex[:10]}@example.test"
    _, user = req("POST", "/ms/v1/identity/register", {"email": email, "displayName": "Course Demo", "password": "ExamDemo123!"})
    token = user["accessToken"]
    print("PASS: register + JWT")
    _, profile = req("GET", "/ms/v1/identity/me", token=token)
    assert profile["role"] == "LEARNER"
    _, quizzes = req("GET", "/ms/v1/contents/quizzes")
    assert len(quizzes) >= 2
    free = next(q for q in quizzes if not q["premium"])
    premium = next(q for q in quizzes if q["premium"])
    _, quiz_detail = req("GET", f"/ms/v1/contents/quizzes/{free['id']}")
    assert all("correctChoice" not in q for q in quiz_detail["questions"])
    print("PASS: public quiz list does not reveal answer key")
    req("POST", "/ms/v1/contents/quizzes", {"title": "Forbidden", "part": 5, "premium": False,
                                                     "questions": [{"prompt":"1?", "a":"A", "b":"B", "c":"C", "d":"D", "answer":"A", "explanation":"No"}]},
        token=token, expected=(403,))
    print("PASS: learner RBAC denies teacher authoring")
    req("GET", f"/ms/v1/contents/quizzes/internal/{free['id']}", expected=(404,))
    print("PASS: internal answer-key route blocked by Gateway")
    req("POST", "/ms/v1/attempts", {"quizId": premium["id"]}, token, expected=(403,))
    print("PASS: FREE user denied premium quiz")
    _, attempt = req("POST", "/ms/v1/attempts", {"quizId": free["id"]}, token)
    assert attempt["status"] == "IN_PROGRESS"
    assert all(q.get("correct") is None and q.get("explanation") is None for q in attempt["questions"])
    qid = attempt["questions"][0]["id"]
    req("PUT", f"/ms/v1/attempts/{attempt['id']}/answers/{qid}", {"choice": "A"}, token)
    _, grade1 = req("POST", f"/ms/v1/attempts/{attempt['id']}/submit", token=token)
    _, grade2 = req("POST", f"/ms/v1/attempts/{attempt['id']}/submit", token=token)
    assert grade1["status"] == "GRADED" and grade1["correct"] == grade2["correct"]
    print("PASS: attempt submission idempotent; explanations returned only after grading")
    _, other = req("POST", "/ms/v1/identity/register", {"email": f"other-{uuid.uuid4().hex[:8]}@example.test", "displayName": "Other User", "password": "ExamDemo123!"})
    req("GET", f"/ms/v1/attempts/{attempt['id']}", token=other["accessToken"], expected=(404,))
    print("PASS: ownership guard")
    _, membership = req("GET", "/api/v1/memberships/me", token=token)
    assert membership["planCode"] == "FREE"
    _, changed = req("PUT", "/api/v1/memberships/me", {"planCode": "PRO"}, token, expected=(200, 403))
    if isinstance(changed, dict) and changed.get("planCode") == "PRO":
        _, premium_attempt = req("POST", "/ms/v1/attempts", {"quizId": premium["id"]}, token)
        assert premium_attempt["status"] == "IN_PROGRESS"
        print("PASS: demo PRO entitlement unlocks premium quiz")
    else:
        print("SKIP: self-enroll disabled; premium remains protected")
    req("PUT", f"/api/v1/interactions/contents/{free['id']}/reviews/me", {"stars":5,"comment":"Bài học hữu ích"}, token)
    _, reviews = req("GET", f"/api/v1/interactions/contents/{free['id']}/reviews")
    assert any(r["stars"] == 5 for r in reviews)
    print("PASS: interaction review")
    for _ in range(12):
        _, activities = req("GET", "/api/v1/interactions/activities/me", token=token)
        if any(a["attemptId"] == attempt["id"] for a in activities):
            break
        time.sleep(2)
    else:
        raise AssertionError("No eventual AttemptGraded activity within 24 seconds (inspect broker/outbox)")
    print("PASS: RabbitMQ outbox -> Interaction activity delivered")
    print("ALL SMOKE ASSERTIONS PASSED")

if __name__ == "__main__":
    run()
