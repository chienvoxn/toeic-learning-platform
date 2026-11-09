
import argparse
import json
from pathlib import Path

import numpy as np
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import roc_auc_score, log_loss
from sklearn.pipeline import make_pipeline
from sklearn.preprocessing import StandardScaler

FEATURES = [
    "repetitionCount",
    "intervalDays",
    "easeFactor",
    "daysSinceLastReview",
    "daysUntilNextReview",
    "totalReviews",
    "averageRating",
    "failureRate",
]


def load_dataset(path):
    records = json.loads(Path(path).read_text(encoding="utf-8"))

    if not isinstance(records, list):
        raise ValueError("Dataset must be a JSON array")

    rows = []

    for record in records:
        features = record["features"]

        x = [float(features[name]) for name in FEATURES]
        y = int(record["label"])

        if y not in (0, 1):
            raise ValueError("Labels must be 0 or 1")

        if not np.isfinite(x).all():
            raise ValueError("Non-finite feature value")

        rows.append((record["reviewedAt"], x, y))

    rows.sort(key=lambda row: row[0])

    if len(rows) < 20:
        raise ValueError("At least 20 examples required")

    x = np.array([row[1] for row in rows])
    y = np.array([row[2] for row in rows])

    return x, y


def train(dataset_path, output_path):
    x, y = load_dataset(dataset_path)

    split = int(len(x) * 0.8)

    x_train, x_valid = x[:split], x[split:]
    y_train, y_valid = y[:split], y[split:]

    if len(np.unique(y_train)) < 2:
        raise ValueError("Training data needs both classes")

    model = make_pipeline(
        StandardScaler(),
        LogisticRegression(
            max_iter=1000,
            class_weight="balanced",
            random_state=42,
        ),
    )

    model.fit(x_train, y_train)

    probabilities = model.predict_proba(x_valid)[:, 1]

    if len(np.unique(y_valid)) == 2:
        print("Validation ROC-AUC:", roc_auc_score(
            y_valid, probabilities
        ))
    else:
        print("Validation ROC-AUC: unavailable (single class)")

    print("Validation log loss:", log_loss(
        y_valid, probabilities, labels=[0, 1]
    ))

    scaler = model.named_steps["standardscaler"]
    classifier = model.named_steps["logisticregression"]

    artifact = {
        "modelType": "logistic_regression",
        "version": 1,
        "features": FEATURES,
        "mean": scaler.mean_.tolist(),
        "scale": scaler.scale_.tolist(),
        "coefficients": classifier.coef_[0].tolist(),
        "intercept": float(classifier.intercept_[0]),
        "trainingExamples": int(len(x_train)),
        "validationExamples": int(len(x_valid)),
    }

    output = Path(output_path)
    output.parent.mkdir(parents=True, exist_ok=True)

    output.write_text(
        json.dumps(artifact, indent=2),
        encoding="utf-8",
    )

    print("Saved model:", output)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()

    parser.add_argument("--dataset", required=True)
    parser.add_argument(
        "--output",
        default="ml/vocabulary/vocabulary_model.json",
    )

    args = parser.parse_args()
    train(args.dataset, args.output)
