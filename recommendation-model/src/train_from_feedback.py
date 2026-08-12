"""Train a lightweight feedback model only after opt-in interaction data exists.

Expected CSV columns:
aqi_safety,preference_match,popularity,travel_ease,weather_suitability,liked
"""

from __future__ import annotations

import csv
import json
from pathlib import Path

from sklearn.linear_model import LogisticRegression


ROOT = Path(__file__).parents[1]
INPUT = ROOT / "data" / "feedback" / "interactions.csv"
OUTPUT = ROOT / "model" / "learned-ranking-model.json"
FEATURES = ["aqi_safety", "preference_match", "popularity", "travel_ease", "weather_suitability"]


def main() -> None:
    if not INPUT.exists():
        raise SystemExit("No feedback file yet. Keep using the baseline model until users opt in.")
    with INPUT.open(newline="", encoding="utf-8") as file:
        rows = list(csv.DictReader(file))
    if len(rows) < 100:
        raise SystemExit("Collect at least 100 opt-in interactions before training.")
    model = LogisticRegression(max_iter=1_000).fit(
        [[float(row[feature]) for feature in FEATURES] for row in rows],
        [int(row["liked"]) for row in rows],
    )
    weights = model.coef_[0]
    scale = sum(abs(weight) for weight in weights) or 1.0
    OUTPUT.write_text(json.dumps({
        "modelVersion": 1,
        "name": "india-feedback-logistic-ranker",
        "features": {"aqiSafety": float(abs(weights[0]) / scale), "preferenceMatch": float(abs(weights[1]) / scale),
                     "popularity": float(abs(weights[2]) / scale), "travelEase": float(abs(weights[3]) / scale),
                     "weatherSuitability": float(abs(weights[4]) / scale)},
        "trainingRows": len(rows)
    }, indent=2), encoding="utf-8")


if __name__ == "__main__":
    main()
