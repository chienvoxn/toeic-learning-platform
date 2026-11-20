"""Register generated users through the application's public API."""
import argparse
import csv
import json
import time
import urllib.error
import urllib.request
from pathlib import Path


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--csv", type=Path, default=Path(__file__).with_name("login-users-10000.csv"))
    parser.add_argument("--base-url", default="http://127.0.0.1:8080")
    parser.add_argument("--delay", type=float, default=0.02)
    args = parser.parse_args()
    if args.delay < 0:
        parser.error("--delay cannot be negative")
    endpoint = args.base_url.rstrip("/") + "/api/v1/auth/register"
    created = existing = 0
    with args.csv.open(newline="", encoding="utf-8") as source:
        for row_number, row in enumerate(csv.reader(source), 1):
            if len(row) != 2:
                raise SystemExit(f"Invalid CSV row {row_number}: expected email,password")
            email, password = row
            body = json.dumps({"email": email, "password": password, "fullName": f"Load Test {row_number}"}).encode()
            request = urllib.request.Request(endpoint, body, {"Content-Type": "application/json"}, method="POST")
            try:
                with urllib.request.urlopen(request, timeout=30) as response:
                    if response.status != 201:
                        raise SystemExit(f"Row {row_number}: unexpected HTTP {response.status}")
                    created += 1
            except urllib.error.HTTPError as error:
                if error.code == 409:
                    existing += 1
                else:
                    raise SystemExit(f"Row {row_number}: HTTP {error.code}; stopping") from None
            except urllib.error.URLError as error:
                raise SystemExit(f"Row {row_number}: connection failed ({error.reason}); stopping") from None
            if row_number % 500 == 0:
                print(f"Processed {row_number} users: {created} created, {existing} already present")
            time.sleep(args.delay)
    print(f"Finished: {created} created, {existing} already present")


if __name__ == "__main__":
    main()
