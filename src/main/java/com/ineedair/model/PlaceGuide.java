package com.ineedair.model;

import java.util.List;

public record PlaceGuide(String name, String summary, String imageUrl, List<String> nearbyHighlights) {
}
