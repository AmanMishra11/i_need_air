package com.ineedair.service;

import com.ineedair.client.NominatimClient;
import com.ineedair.client.OpenMeteoClient;
import com.ineedair.client.OverpassClient;
import com.ineedair.client.WikipediaClient;
import com.ineedair.model.AirSnapshot;
import com.ineedair.model.DestinationAssessment;
import com.ineedair.model.Place;
import com.ineedair.model.WeatherSnapshot;
import com.ineedair.model.TravelMood;
import com.ineedair.store.LocalDataStore;
import org.springframework.stereotype.Service;
import jakarta.annotation.PreDestroy;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class DestinationService {
    private final NominatimClient nominatimClient;
    private final OpenMeteoClient openMeteoClient;
    private final OverpassClient overpassClient;
    private final WikipediaClient wikipediaClient;
    private final LocalDataStore localDataStore;
    private final ExecutorService recommendationExecutor = Executors.newFixedThreadPool(4);

    public DestinationService(NominatimClient nominatimClient, OpenMeteoClient openMeteoClient, OverpassClient overpassClient,
                              WikipediaClient wikipediaClient, LocalDataStore localDataStore) {
        this.nominatimClient = nominatimClient;
        this.openMeteoClient = openMeteoClient;
        this.overpassClient = overpassClient;
        this.wikipediaClient = wikipediaClient;
        this.localDataStore = localDataStore;
    }

    public List<Place> search(String query) {
        return nominatimClient.search(query);
    }

    public Place currentLocation(double latitude, double longitude) {
        return nominatimClient.reverse(latitude, longitude);
    }

    public com.ineedair.model.TripWeather tripWeather(Place place, java.time.LocalDate start, java.time.LocalDate end) {
        return openMeteoClient.tripWeather(place.latitude(), place.longitude(), start, end);
    }

    public DestinationAssessment assess(Place place) {
        var cached = localDataStore.cachedAssessment(place);
        if (cached.isPresent()) {
            return cached.get();
        }
        AirSnapshot air = openMeteoClient.currentAir(place.latitude(), place.longitude());
        WeatherSnapshot weather = openMeteoClient.currentWeather(place.latitude(), place.longitude());
        DestinationAssessment assessment = new DestinationAssessment(place, air, weather, calculateScore(air, weather));
        localDataStore.cacheAssessment(assessment);
        return assessment;
    }

    public List<DestinationAssessment> bestDestinationsNear(Place origin, int radiusKilometres) {
        return bestDestinationsNear(origin, radiusKilometres, TravelMood.EXPLORE);
    }

    public List<DestinationAssessment> bestDestinationsNear(Place origin, int radiusKilometres, TravelMood mood) {
        List<Place> candidates = overpassClient.nearbyPlaces(origin, radiusKilometres, mood);
        if (candidates.isEmpty() && mood != TravelMood.EXPLORE) {
            candidates = overpassClient.nearbyPlaces(origin, radiusKilometres, TravelMood.EXPLORE);
        }
        List<CompletableFuture<DestinationAssessment>> work = candidates.stream()
                .map(place -> CompletableFuture.supplyAsync(() -> assess(place), recommendationExecutor))
                .toList();
        return work.stream()
                .map(CompletableFuture::join)
                .sorted(java.util.Comparator.<DestinationAssessment>comparingDouble(assessment -> assessment.air().aqi())
                        .thenComparing(java.util.Comparator.comparingInt(DestinationAssessment::healthScore).reversed()))
                .toList();
    }

    @PreDestroy
    void shutDownExecutor() {
        recommendationExecutor.shutdownNow();
    }

    public com.ineedair.model.PlaceGuide placeGuide(Place place) {
        var cached = localDataStore.cachedGuide(place);
        if (cached.isPresent()) {
            return cached.get();
        }
        var guide = wikipediaClient.guideFor(place);
        localDataStore.cacheGuide(place, guide);
        return guide;
    }

    public void saveFavourite(Place place) {
        localDataStore.saveFavourite(place);
    }

    public List<com.ineedair.model.FavouritePlace> favourites() {
        return localDataStore.favourites();
    }

    private int calculateScore(AirSnapshot air, WeatherSnapshot weather) {
        int aqiScore = air.aqi() <= 50 ? 55 : air.aqi() <= 100 ? 40 : air.aqi() <= 150 ? 22 : 5;
        int temperatureScore = weather.temperatureCelsius() >= 16 && weather.temperatureCelsius() <= 30 ? 25 : 14;
        int rainScore = weather.rainProbability() <= 30 ? 12 : weather.rainProbability() <= 60 ? 7 : 2;
        int windScore = weather.windSpeedKph() <= 25 ? 8 : 4;
        return aqiScore + temperatureScore + rainScore + windScore;
    }
}
