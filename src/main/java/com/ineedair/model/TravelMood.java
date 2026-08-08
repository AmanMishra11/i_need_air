package com.ineedair.model;

public enum TravelMood {
    PEACE("Peace & nature"),
    PARTY("Party & nightlife"),
    HISTORICAL("Historical"),
    RELIGIOUS("Religious"),
    BEACH("Beach"),
    EXPLORE("Explore anything");

    private final String label;

    TravelMood(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
