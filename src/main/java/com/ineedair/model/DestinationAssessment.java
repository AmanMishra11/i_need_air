package com.ineedair.model;

public record DestinationAssessment(Place place, AirSnapshot air, WeatherSnapshot weather, int healthScore) {
}
