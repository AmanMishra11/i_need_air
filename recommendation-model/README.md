# I Need Air — India recommendation model

This is an **offline data and model workspace**. It is separate from the Java desktop app so large source extracts and generated datasets never enter the application repository.

## What it does now

`src/ranker.py` is a transparent hybrid ranker. It scores an attraction using live environmental data plus durable place facts:

- AQI safety (40%)
- trip-type match (25%)
- popularity proxy (15%)
- distance / travel effort (10%)
- weather suitability (10%)

The model configuration is [initial-ranking-model.json](model/initial-ranking-model.json). The Java app can consume this JSON later without requiring Python at runtime.

## Data sources

| Source | Purpose | Notes |
|---|---|---|
| OpenStreetMap / Geofabrik India extract | places, tags, coordinates | Keep ODbL attribution and licence obligations. |
| Wikidata | categories, heritage facts, Wikipedia links | Structured data is CC0. |
| Wikimedia Pageviews | popularity proxy | Aggregate counts; do not store user-level data. |
| Live app APIs | AQI, weather, routing | Fetched at recommendation time. |

Do not commit files under `data/raw/` or `data/processed/`. These are intentionally ignored.

## Workflow

1. Acquire a regional India OSM extract and a focused Wikidata export outside Git.
2. Normalise them into `data/raw/places.jsonl` using the schema in [place-record.schema.json](schema/place-record.schema.json).
3. Run `python src/build_features.py` to create `data/processed/place-features.jsonl`.
4. Use the baseline model immediately.
5. Once real opt-in user events exist, export anonymous interactions and run `python src/train_from_feedback.py` to produce an updated JSON model.

## Honest model behaviour

There is no real preference-training signal before users interact with the product. The initial model is an explainable ranking formula, not a falsely “trained AI.” The feedback trainer is what turns it into a learned personalized ranker later.

