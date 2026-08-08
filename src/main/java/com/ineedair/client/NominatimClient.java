package com.ineedair.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ineedair.model.Place;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

@Component
public class NominatimClient {
    private final WebClient client = WebClient.builder()
            .baseUrl("https://nominatim.openstreetmap.org")
            .defaultHeader("User-Agent", "INeedAir/0.1 portfolio desktop application")
            .build();

    public List<Place> search(String query) {
        NominatimResult[] results = client.get()
                .uri(uriBuilder -> uriBuilder.path("/search")
                        .queryParam("q", query)
                        .queryParam("format", "jsonv2")
                        .queryParam("limit", 5)
                        .build())
                .retrieve()
                .bodyToMono(NominatimResult[].class)
                .block();

        if (results == null) {
            return List.of();
        }
        return java.util.Arrays.stream(results)
                .map(result -> new Place(result.displayName(), Double.parseDouble(result.lat()), Double.parseDouble(result.lon())))
                .toList();
    }

    public Place reverse(double latitude, double longitude) {
        NominatimResult result = client.get()
                .uri(builder -> builder.path("/reverse")
                        .queryParam("lat", latitude).queryParam("lon", longitude)
                        .queryParam("format", "jsonv2").build())
                .retrieve().bodyToMono(NominatimResult.class).block();
        if (result == null) {
            return new Place("Selected map location", latitude, longitude);
        }
        return new Place(result.displayName(), latitude, longitude);
    }

    private record NominatimResult(@JsonProperty("display_name") String displayName, String lat, String lon) {
    }
}
