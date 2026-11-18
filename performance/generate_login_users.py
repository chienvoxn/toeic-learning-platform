"""Generate private, reproducible login test credentials (not database rows)."""
import argparse
import csv
import secrets
from pathlib import Path


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--count", type=int, default=10000)
    parser.add_argument("--output", type=Path, default=Path(__file__).with_name("login-users-10000.csv"))
    args = parser.parse_args()
    if args.count < 1:
        parser.error("--count must be positive")
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open("w", newline="", encoding="utf-8") as out:
        writer = csv.writer(out)
        for number in range(1, args.count + 1):
            writer.writerow((f"loadtest-{number:05d}@example.invalid", secrets.token_urlsafe(18)))
    print(f"Created {args.count} credential rows at {args.output}")


if __name__ == "__main__":
    main()
