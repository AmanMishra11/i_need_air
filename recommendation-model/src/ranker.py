"""A portable, explainable baseline ranker for I Need Air."""

from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path
from typing import Mapping


@dataclass(frozen=True)
class PlaceFeatures:
    aqi_safety: float
    preference_match: float
    popularity: float
    travel_ease: float
    weather_suitability: float


class HybridRanker:
    def __init__(self, model_path: Path) -> None:
        payload = json.loads(model_path.read_text(encoding="utf-8"))
        self.weights: Mapping[str, float] = payload["features"]

    def score(self, features: PlaceFeatures) -> float:
        values = {
            "aqiSafety": features.aqi_safety,
            "preferenceMatch": features.preference_match,
            "popularity": features.popularity,
            "travelEase": features.travel_ease,
            "weatherSuitability": features.weather_suitability,
        }
        return round(sum(self.weights[name] * max(0.0, min(1.0, value)) for name, value in values.items()) * 100, 1)


if __name__ == "__main__":
    ranker = HybridRanker(Path(__file__).parents[1] / "model" / "initial-ranking-model.json")
    example = PlaceFeatures(0.95, 1.0, 0.65, 0.75, 0.85)
    print(f"Example recommendation score: {ranker.score(example)}/100")
