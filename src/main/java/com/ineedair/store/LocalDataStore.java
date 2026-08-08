package com.ineedair.store;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ineedair.model.AirSnapshot;
import com.ineedair.model.DestinationAssessment;
import com.ineedair.model.FavouritePlace;
import com.ineedair.model.Place;
import com.ineedair.model.PlaceGuide;
import com.ineedair.model.WeatherSnapshot;
import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class LocalDataStore {
    private static final Duration CONDITIONS_TTL = Duration.ofMinutes(10);
    private static final Duration GUIDE_TTL = Duration.ofDays(7);

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public LocalDataStore(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void initialise() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS condition_cache (
                    cache_key TEXT PRIMARY KEY, name TEXT NOT NULL, latitude REAL NOT NULL, longitude REAL NOT NULL,
                    aqi REAL, pm25 REAL, pm10 REAL, temperature REAL, rain_probability REAL, wind_speed REAL,
                    health_score INTEGER, updated_at INTEGER NOT NULL
                )""");
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS place_guides (
                    cache_key TEXT PRIMARY KEY, name TEXT NOT NULL, summary TEXT NOT NULL, image_url TEXT,
                    highlights_json TEXT NOT NULL, updated_at INTEGER NOT NULL
                )""");
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS favourites (
                    id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, latitude REAL NOT NULL,
                    longitude REAL NOT NULL, saved_at INTEGER NOT NULL,
                    UNIQUE(name, latitude, longitude)
                )""");
    }

    public Optional<DestinationAssessment> cachedAssessment(Place place) {
        long oldestAccepted = Instant.now().minus(CONDITIONS_TTL).toEpochMilli();
        List<DestinationAssessment> results = jdbc.query("""
                        SELECT aqi, pm25, pm10, temperature, rain_probability, wind_speed, health_score
                        FROM condition_cache WHERE cache_key = ? AND updated_at >= ?""",
                (row, index) -> new DestinationAssessment(place,
                        new AirSnapshot(row.getDouble("aqi"), row.getDouble("pm25"), row.getDouble("pm10")),
                        new WeatherSnapshot(row.getDouble("temperature"), row.getDouble("rain_probability"), row.getDouble("wind_speed")),
                        row.getInt("health_score")), cacheKey(place), oldestAccepted);
        return results.stream().findFirst();
    }

    public void cacheAssessment(DestinationAssessment assessment) {
        Place place = assessment.place();
        jdbc.update("""
                        INSERT INTO condition_cache(cache_key, name, latitude, longitude, aqi, pm25, pm10, temperature,
                        rain_probability, wind_speed, health_score, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT(cache_key) DO UPDATE SET aqi=excluded.aqi, pm25=excluded.pm25, pm10=excluded.pm10,
                        temperature=excluded.temperature, rain_probability=excluded.rain_probability, wind_speed=excluded.wind_speed,
                        health_score=excluded.health_score, updated_at=excluded.updated_at""",
                cacheKey(place), place.name(), place.latitude(), place.longitude(), assessment.air().aqi(), assessment.air().pm25(),
                assessment.air().pm10(), assessment.weather().temperatureCelsius(), assessment.weather().rainProbability(),
                assessment.weather().windSpeedKph(), assessment.healthScore(), Instant.now().toEpochMilli());
    }

    public Optional<PlaceGuide> cachedGuide(Place place) {
        long oldestAccepted = Instant.now().minus(GUIDE_TTL).toEpochMilli();
        List<PlaceGuide> results = jdbc.query("SELECT name, summary, image_url, highlights_json FROM place_guides WHERE cache_key = ? AND updated_at >= ?",
                (row, index) -> new PlaceGuide(row.getString("name"), row.getString("summary"), row.getString("image_url"),
                        readHighlights(row.getString("highlights_json"))), cacheKey(place), oldestAccepted);
        return results.stream().findFirst();
    }

    public void cacheGuide(Place place, PlaceGuide guide) {
        jdbc.update("""
                        INSERT INTO place_guides(cache_key, name, summary, image_url, highlights_json, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?)
                        ON CONFLICT(cache_key) DO UPDATE SET summary=excluded.summary, image_url=excluded.image_url,
                        highlights_json=excluded.highlights_json, updated_at=excluded.updated_at""",
                cacheKey(place), guide.name(), guide.summary(), guide.imageUrl(), writeHighlights(guide.nearbyHighlights()),
                Instant.now().toEpochMilli());
    }

    public void saveFavourite(Place place) {
        jdbc.update("INSERT OR IGNORE INTO favourites(name, latitude, longitude, saved_at) VALUES (?, ?, ?, ?)",
                place.name(), place.latitude(), place.longitude(), Instant.now().toEpochMilli());
    }

    public List<FavouritePlace> favourites() {
        return jdbc.query("SELECT id, name, latitude, longitude, saved_at FROM favourites ORDER BY saved_at DESC", (row, index) ->
                new FavouritePlace(row.getLong("id"), new Place(row.getString("name"), row.getDouble("latitude"),
                        row.getDouble("longitude")), row.getLong("saved_at")));
    }

    private String cacheKey(Place place) {
        return place.name().toLowerCase() + "|" + Math.round(place.latitude() * 10_000) + "|" + Math.round(place.longitude() * 10_000);
    }

    private List<String> readHighlights(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() { });
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }

    private String writeHighlights(List<String> highlights) {
        try {
            return objectMapper.writeValueAsString(highlights);
        } catch (JsonProcessingException exception) {
            return "[]";
        }
    }
}
