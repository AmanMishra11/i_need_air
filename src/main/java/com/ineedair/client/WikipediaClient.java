package com.ineedair.client;

import com.ineedair.model.Place;
import com.ineedair.model.PlaceGuide;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Component
public class WikipediaClient {
    private final WebClient client = WebClient.builder()
            .baseUrl("https://en.wikipedia.org")
            .defaultHeader("User-Agent", "INeedAir/0.1 portfolio desktop application")
            .build();

    public PlaceGuide guideFor(Place place) {
        PageDetails summary = pageDetails(place.name());

        GeoSearchResponse nearby = client.get()
                .uri(builder -> builder.path("/w/api.php")
                        .queryParam("action", "query").queryParam("format", "json")
                        .queryParam("list", "geosearch")
                        .queryParam("gscoord", place.latitude() + "|" + place.longitude())
                        .queryParam("gsradius", 10000).queryParam("gslimit", 5)
                        .queryParam("gsnamespace", 0).build())
                .retrieve().bodyToMono(GeoSearchResponse.class)
                .onErrorReturn(new GeoSearchResponse(null)).block();

        List<String> highlights = nearby != null && nearby.query() != null && nearby.query().geosearch() != null
                ? nearby.query().geosearch().stream().map(GeoSearchItem::title)
                        .filter(title -> !title.equalsIgnoreCase(place.name())).toList()
                : List.of();
        String description = summary != null && summary.extract() != null && !summary.extract().isBlank()
                ? summary.extract() : "Explore this destination and its nearby landmarks.";
        String imageUrl = summary != null && summary.thumbnail() != null ? summary.thumbnail().source() : null;
        if (imageUrl == null && !highlights.isEmpty()) {
            PageDetails landmark = pageDetails(highlights.getFirst());
            imageUrl = landmark != null && landmark.thumbnail() != null ? landmark.thumbnail().source() : null;
        }
        return new PlaceGuide(place.name(), description, imageUrl, highlights);
    }

    private PageDetails pageDetails(String title) {
        PageResponse response = client.get()
                .uri(builder -> builder.path("/w/api.php")
                        .queryParam("action", "query").queryParam("format", "json")
                        .queryParam("prop", "extracts|pageimages").queryParam("titles", title)
                        .queryParam("exintro", true).queryParam("explaintext", true)
                        .queryParam("pithumbsize", 600).build())
                .retrieve().bodyToMono(PageResponse.class)
                .onErrorReturn(new PageResponse(null)).block();
        if (response == null || response.query() == null || response.query().pages() == null) {
            return null;
        }
        return response.query().pages().values().stream().findFirst().orElse(null);
    }

    private record PageResponse(PageQuery query) { }
    private record PageQuery(Map<String, PageDetails> pages) { }
    private record PageDetails(String extract, Thumbnail thumbnail) { }
    private record Thumbnail(String source) { }
    private record GeoSearchResponse(GeoQuery query) { }
    private record GeoQuery(List<GeoSearchItem> geosearch) { }
    private record GeoSearchItem(String title) { }
}
