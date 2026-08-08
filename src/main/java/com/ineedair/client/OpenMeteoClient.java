package com.ineedair.client;

import com.ineedair.model.AirSnapshot;
import com.ineedair.model.WeatherSnapshot;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import com.ineedair.model.TripWeather;

import java.time.LocalDate;

@Component
public class OpenMeteoClient {
    private final WebClient weatherClient = WebClient.create("https://api.open-meteo.com/v1");
    private final WebClient airClient = WebClient.create("https://air-quality-api.open-meteo.com/v1");

    public WeatherSnapshot currentWeather(double latitude, double longitude) {
        WeatherResponse response = weatherClient.get().uri(builder -> builder.path("/forecast")
                        .queryParam("latitude", latitude)
                        .queryParam("longitude", longitude)
                        .queryParam("current", "temperature_2m,wind_speed_10m")
                        .queryParam("hourly", "precipitation_probability")
                        .queryParam("forecast_days", 1)
                        .build())
                .retrieve().bodyToMono(WeatherResponse.class).block();
        if (response == null || response.current() == null) {
            throw new IllegalStateException("Weather data is not available right now.");
        }
        double rainChance = response.hourly() != null && response.hourly().precipitationProbability() != null
                ? response.hourly().precipitationProbability()[0] : 0;
        return new WeatherSnapshot(response.current().temperature2m(), rainChance, response.current().windSpeed10m());
    }

    public AirSnapshot currentAir(double latitude, double longitude) {
        AirResponse response = airClient.get().uri(builder -> builder.path("/air-quality")
                        .queryParam("latitude", latitude)
                        .queryParam("longitude", longitude)
                        .queryParam("current", "us_aqi,pm2_5,pm10")
                        .build())
                .retrieve().bodyToMono(AirResponse.class).block();
        if (response == null || response.current() == null) {
            throw new IllegalStateException("Air-quality data is not available right now.");
        }
        return new AirSnapshot(response.current().usAqi(), response.current().pm25(), response.current().pm10());
    }

    public TripWeather tripWeather(double latitude, double longitude, LocalDate start, LocalDate end) {
        TripWeatherResponse response = weatherClient.get().uri(builder -> builder.path("/forecast")
                        .queryParam("latitude", latitude).queryParam("longitude", longitude)
                        .queryParam("daily", "temperature_2m_max,temperature_2m_min,precipitation_probability_max")
                        .queryParam("timezone", "auto").queryParam("start_date", start).queryParam("end_date", end).build())
                .retrieve().bodyToMono(TripWeatherResponse.class).block();
        if (response == null || response.daily() == null) return new TripWeather("Forecast unavailable");
        double max = java.util.Arrays.stream(response.daily().max()).max().orElse(0);
        double min = java.util.Arrays.stream(response.daily().min()).min().orElse(0);
        double rain = java.util.Arrays.stream(response.daily().rain()).max().orElse(0);
        return new TripWeather(String.format("%.0f–%.0f°C; up to %.0f%% rain probability", min, max, rain));
    }

    private record WeatherResponse(CurrentWeather current, HourlyWeather hourly) { }
    private record CurrentWeather(@com.fasterxml.jackson.annotation.JsonProperty("temperature_2m") double temperature2m,
                                  @com.fasterxml.jackson.annotation.JsonProperty("wind_speed_10m") double windSpeed10m) { }
    private record HourlyWeather(@com.fasterxml.jackson.annotation.JsonProperty("precipitation_probability") double[] precipitationProbability) { }
    private record AirResponse(CurrentAir current) { }
    private record CurrentAir(@com.fasterxml.jackson.annotation.JsonProperty("us_aqi") double usAqi,
                              @com.fasterxml.jackson.annotation.JsonProperty("pm2_5") double pm25,
                              @com.fasterxml.jackson.annotation.JsonProperty("pm10") double pm10) { }
    private record TripWeatherResponse(DailyTripWeather daily) { }
    private record DailyTripWeather(@com.fasterxml.jackson.annotation.JsonProperty("temperature_2m_max") double[] max,
                                    @com.fasterxml.jackson.annotation.JsonProperty("temperature_2m_min") double[] min,
                                    @com.fasterxml.jackson.annotation.JsonProperty("precipitation_probability_max") double[] rain) { }
}
