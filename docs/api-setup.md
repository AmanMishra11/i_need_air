# API and AI setup

I Need Air uses free public endpoints for AQI, weather, map tiles, place search, place discovery, and place information. No key is needed for those features.

## Optional Gemini key

1. Copy `.env.example` to `.env` in the project root.
2. Change the following values in `.env`:

   ```properties
   AI_PROVIDER=gemini
   GEMINI_API_KEY=your-key
   GEMINI_MODEL=gemini-2.5-flash
   ```

3. Restart the app.

The `.env` file is ignored by Git. Do not paste the key into Java files, `application.properties`, the README, or commit messages.

## Ollama (recommended local option)

Ollama does not need an API key. Install Ollama, then run:

```powershell
ollama pull llama3.2
```

Leave this in `.env`:

```properties
AI_PROVIDER=ollama
OLLAMA_MODEL=llama3.2
```

If Ollama is not installed or running, I Need Air falls back to its built-in itinerary planner.

## Future route API

`OPENROUTESERVICE_API_KEY` is reserved for route integration. It is not used by the current app yet, so do not create a key unless that feature is added.
