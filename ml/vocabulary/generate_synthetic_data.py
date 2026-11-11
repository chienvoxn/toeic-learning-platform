
import argparse
import json
import math
import random
from datetime import datetime, timedelta, timezone
from pathlib import Path
from uuid import uuid5, NAMESPACE_DNS


def sigmoid(x):
    return 1 / (1 + math.exp(-max(-40, min(40, x))))


def generate(seed, words, reviews):
    rng = random.Random(seed)
    records = []
    start = datetime(2025, 1, 1, tzinfo=timezone.utc)

    for i in range(words):
        vocab_id = str(uuid5(NAMESPACE_DNS, f"synthetic-{i}"))
        difficulty = rng.gauss(0, 0.8)
        repetition = 0
        interval = 0
        ease = 2.5
        ratings = []

        reviewed_at = start + timedelta(days=rng.uniform(0, 90))
        next_review_at = reviewed_at
        last_reviewed_at = None

        for j in range(reviews):
            if j > 0:
                delay = max(0, rng.gauss(0.3, 0.8))
                reviewed_at = next_review_at + timedelta(days=delay)

            observation_end = start + timedelta(days=180)

            if reviewed_at > observation_end:
                break

            days_since = (
                (reviewed_at - last_reviewed_at).total_seconds() / 86400
                if last_reviewed_at else 0.0
            )
            days_until = (
                (next_review_at - reviewed_at).total_seconds() / 86400
            )

            count = len(ratings)
            features = {
                "repetitionCount": repetition,
                "intervalDays": interval,
                "easeFactor": ease,
                "daysSinceLastReview": days_since,
                "daysUntilNextReview": days_until,
                "totalReviews": count,
                "averageRating": sum(ratings) / count if count else 0.0,
                "failureRate": ratings.count(1) / count if count else 0.0,
            }

            score = (
                -1.0 + difficulty
                + 0.12 * math.log1p(max(0, days_since))
                - 0.10 * repetition
                + rng.gauss(0, 0.6)
            )

            if rng.random() < sigmoid(score):
                rating = 1
            else:
                rating = rng.choices([2, 3, 4], weights=[2, 6, 2])[0]

            if j > 0:
                records.append({
                    "vocabularyId": vocab_id,
                    "reviewedAt": reviewed_at.isoformat(),
                    "features": features,
                    "label": int(rating == 1),
                })

            previous_interval = interval

            if rating == 1:
                repetition = 0
                interval = 0
                ease -= 0.20
            elif rating == 2:
                repetition = 0
                interval = 1
                ease -= 0.15
            elif rating == 3:
                repetition += 1
                if repetition == 1:
                    interval = 1
                elif repetition == 2:
                    interval = 6
                else:
                    interval = max(
                        previous_interval + 1,
                        math.floor(previous_interval * ease + 0.5),
                    )
            else:
                repetition += 1
                ease += 0.15
                if repetition == 1:
                    interval = 3
                elif repetition == 2:
                    interval = 8
                else:
                    interval = max(
                        previous_interval + 1,
                        math.floor(previous_interval * ease * 1.3 + 0.5),
                    )

            ease = round(max(1.30, min(3.00, ease)), 2)
            last_reviewed_at = reviewed_at
            next_review_at = reviewed_at + (
                timedelta(minutes=10)
                if rating == 1 else timedelta(days=interval)
            )
            ratings.append(rating)

    records.sort(key=lambda row: row["reviewedAt"])
    return records


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--words", type=int, default=200)
    parser.add_argument("--reviews", type=int, default=12)
    args = parser.parse_args()

    records = generate(args.seed, args.words, args.reviews)
    output = Path("ml/vocabulary/data/synthetic_training.json")
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(records, indent=2), encoding="utf-8")

    positives = sum(row["label"] for row in records)
    print("Dataset: SYNTHETIC")
    print("Examples:", len(records))
    print("Label 1:", positives)
    print("Label 0:", len(records) - positives)
    print("Saved:", output)
