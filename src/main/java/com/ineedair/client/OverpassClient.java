package com.ineedair.client;

import com.ineedair.model.Place;
import com.ineedair.model.TravelMood;
import org.springframework.stereotype.Component;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Finds populated places near an origin using OpenStreetMap's Overpass API.
 * The query is deliberately small and runs only after the user submits a search.
 */
@Component
public class OverpassClient {
    private final WebClient client = WebClient.builder()
            .baseUrl("https://overpass-api.de")
            .defaultHeader("User-Agent", "INeedAir/0.1 portfolio desktop application")
            .build();

    public List<Place> nearbyPlaces(Place origin, int radiusKilometres) {
        return nearbyPlaces(origin, radiusKilometres, TravelMood.EXPLORE);
    }

    public List<Place> nearbyPlaces(Place origin, int radiusKilometres, TravelMood mood) {
        String aroundClause = "(around:" + (radiusKilometres * 1000) + "," + origin.latitude() + "," + origin.longitude() + ");";
        String query = "[out:json][timeout:25];(" + selectorFor(mood, aroundClause) + ");out center 35;";

        OverpassResponse response = client.post()
                .uri("/api/interpreter")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData("data", query))
                .retrieve()
                .bodyToMono(OverpassResponse.class)
                .block();

        if (response == null || response.elements() == null) {
            return List.of();
        }
        return response.elements().stream()
                .filter(element -> element.tags() != null && element.tags().name() != null && element.latitude() != null)
                .map(element -> new Place(element.tags().name(), element.latitude(), element.longitude()))
                .filter(place -> distanceInKilometres(origin, place) >= 3)
                .sorted(Comparator.comparingDouble(place -> distanceInKilometres(origin, place)))
                .limit(12)
                .toList();
    }

    private String selectorFor(TravelMood mood, String aroundClause) {
        return switch (mood) {
            case PEACE -> "nwr[\"leisure\"=\"park\"]" + aroundClause + "nwr[\"tourism\"=\"viewpoint\"]" + aroundClause
                    + "nwr[\"natural\"~\"^(peak|waterfall)$\"]" + aroundClause;
            case PARTY -> "nwr[\"amenity\"~\"^(nightclub|bar|pub)$\"]" + aroundClause;
            case HISTORICAL -> "nwr[\"historic\"]" + aroundClause + "nwr[\"tourism\"=\"museum\"]" + aroundClause;
            case RELIGIOUS -> "nwr[\"amenity\"=\"place_of_worship\"]" + aroundClause;
            case BEACH -> "nwr[\"natural\"=\"beach\"]" + aroundClause;
            case EXPLORE -> "node[\"place\"~\"^(city|town|village)$\"]" + aroundClause;
        };
    }

    private double distanceInKilometres(Place first, Place second) {
        double latitudeDelta = Math.toRadians(second.latitude() - first.latitude());
        double longitudeDelta = Math.toRadians(second.longitude() - first.longitude());
        double value = Math.sin(latitudeDelta / 2) * Math.sin(latitudeDelta / 2)
                + Math.cos(Math.toRadians(first.latitude())) * Math.cos(Math.toRadians(second.latitude()))
                * Math.sin(longitudeDelta / 2) * Math.sin(longitudeDelta / 2);
        return 6371 * 2 * Math.atan2(Math.sqrt(value), Math.sqrt(1 - value));
    }

    private record OverpassResponse(List<OverpassElement> elements) { }
    private record OverpassElement(Double lat, Double lon, OverpassCenter center, OverpassTags tags) {
        Double latitude() { return lat != null ? lat : center == null ? null : center.lat(); }
        Double longitude() { return lon != null ? lon : center == null ? null : center.lon(); }
    }
    private record OverpassCenter(Double lat, Double lon) { }
    private record OverpassTags(String name) { }
}
