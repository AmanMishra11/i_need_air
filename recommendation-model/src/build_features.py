"""Validate raw India place records and create the feature feed for the ranker.

Input and output use JSON Lines so the pipeline works without a database and can
later be replaced by a DuckDB/PostgreSQL workflow for larger extracts.
"""

from __future__ import annotations

import json
from pathlib import Path


ROOT = Path(__file__).parents[1]
SOURCE = ROOT / "data" / "raw" / "places.jsonl"
TARGET = ROOT / "data" / "processed" / "place-features.jsonl"


def normalize(record: dict) -> dict:
    categories = {item.lower() for item in record.get("categories", [])}
    return {
        "placeId": record["placeId"],
        "name": record["name"],
        "latitude": float(record["latitude"]),
        "longitude": float(record["longitude"]),
        "categories": sorted(categories),
        "popularity": min(1.0, max(0.0, float(record.get("popularity", 0)))),
        "osmTagCount": int(record.get("osmTagCount", 0)),
    }


def main() -> None:
    if not SOURCE.exists():
        raise SystemExit(f"Missing {SOURCE}. See README.md for the input schema.")
    TARGET.parent.mkdir(parents=True, exist_ok=True)
    with SOURCE.open(encoding="utf-8") as source, TARGET.open("w", encoding="utf-8") as target:
        for line in source:
            if line.strip():
                target.write(json.dumps(normalize(json.loads(line))) + "\n")


if __name__ == "__main__":
    main()
