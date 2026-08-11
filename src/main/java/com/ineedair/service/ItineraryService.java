package com.ineedair.service;

import com.ineedair.model.DestinationAssessment;
import com.ineedair.model.ItineraryResult;
import com.ineedair.model.PlaceGuide;
import com.ineedair.model.TripWeather;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDate;

@Service
public class ItineraryService {
    private final DestinationService destinationService;
    private final String provider;
    private final String geminiKey;
    private final String geminiModel;
    private final String ollamaModel;

    public ItineraryService(DestinationService destinationService,
                            @Value("${ai.provider:template}") String provider,
                            @Value("${GEMINI_API_KEY:}") String geminiKey,
                            @Value("${GEMINI_MODEL:gemini-2.5-flash}") String geminiModel,
                            @Value("${OLLAMA_MODEL:llama3.2}") String ollamaModel) {
        this.destinationService = destinationService;
        this.provider = provider;
        this.geminiKey = geminiKey;
        this.geminiModel = geminiModel;
        this.ollamaModel = ollamaModel;
    }

    public ItineraryResult create(DestinationAssessment destination, LocalDate start, LocalDate end, String intent, TripWeather weather) {
        PlaceGuide guide = destinationService.placeGuide(destination.place());
        String prompt = prompt(destination, guide, start, end, intent, weather);
        if ("gemini".equalsIgnoreCase(provider) && !geminiKey.isBlank()) {
            String answer = WebClient.create("https://generativelanguage.googleapis.com").post()
                    .uri("/v1beta/models/{model}:generateContent?key={key}", geminiModel, geminiKey)
                    .bodyValue(java.util.Map.of("contents", java.util.List.of(java.util.Map.of("parts", java.util.List.of(java.util.Map.of("text", prompt))))) )
                    .retrieve().bodyToMono(GeminiResponse.class).map(GeminiResponse::text).block();
            if (answer != null && !answer.isBlank()) return new ItineraryResult(answer, "Gemini");
        }
        if ("ollama".equalsIgnoreCase(provider)) {
            String answer = WebClient.create("http://localhost:11434").post().uri("/api/generate")
                    .bodyValue(java.util.Map.of("model", ollamaModel, "prompt", prompt, "stream", false))
                    .retrieve().bodyToMono(OllamaResponse.class).map(OllamaResponse::response).onErrorReturn("").block();
            if (answer != null && !answer.isBlank()) return new ItineraryResult(answer, "Ollama (local)");
        }
        return new ItineraryResult(template(destination, guide, start, end, intent, weather), "Local planner");
    }

    private String prompt(DestinationAssessment d, PlaceGuide guide, LocalDate start, LocalDate end, String intent, TripWeather weather) {
        return "Create a practical day-by-day travel itinerary. Use only the provided facts; do not invent opening hours, prices, or transport schedules. "
                + "Destination: " + d.place().name() + ". Dates: " + start + " to " + end + ". Travel intent: " + intent
                + ". AQI: " + Math.round(d.air().aqi()) + ". Weather: " + weather.summary() + ". Nearby highlights: "
                + String.join(", ", guide.nearbyHighlights()) + ". Include morning, afternoon, evening, practical tips, and a bad-weather alternative.";
    }

    private String template(DestinationAssessment d, PlaceGuide guide, LocalDate start, LocalDate end, String intent, TripWeather weather) {
        long days = java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1;
        String highlights = guide.nearbyHighlights().isEmpty() ? "the local centre and nearby landmarks" : String.join(", ", guide.nearbyHighlights());
        return "YOUR " + days + "-DAY " + intent.toUpperCase() + " ITINERARY\n\n"
                + "Destination: " + d.place().name() + "\nAir quality: AQI " + Math.round(d.air().aqi()) + "\nWeather: " + weather.summary() + "\n\n"
                + "Day 1 — Start with " + highlights + ". Plan outdoor time for the most comfortable part of the day and keep an indoor café or museum alternative.\n\n"
                + "Day 2 onward — Choose one nearby highlight each morning, leave afternoons flexible for local food and walking, and reserve evenings for the atmosphere that matches your " + intent + " preference.\n\n"
                + "Practical tip: recheck AQI and weather on the morning of each outing. This offline plan is a starting point; enable Gemini or Ollama in settings for a personalized narrative itinerary.";
    }

    private record GeminiResponse(java.util.List<Candidate> candidates) {
        String text() { return candidates == null || candidates.isEmpty() ? "" : candidates.getFirst().content().parts().getFirst().text(); }
    }
    private record Candidate(Content content) { }
    private record Content(java.util.List<Part> parts) { }
    private record Part(String text) { }
    private record OllamaResponse(String response) { }
}
