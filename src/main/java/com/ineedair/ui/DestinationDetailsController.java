package com.ineedair.ui;

import com.ineedair.model.DestinationAssessment;
import com.ineedair.model.ItineraryResult;
import com.ineedair.model.TripWeather;
import com.ineedair.service.DestinationService;
import com.ineedair.service.ItineraryService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.concurrent.CompletableFuture;

@Component
public class DestinationDetailsController {
    private final AppNavigator navigator;
    private final DestinationService destinationService;
    private final ItineraryService itineraryService;
    private DestinationAssessment destination;
    @FXML private Label destinationName;
    @FXML private Label airLabel;
    @FXML private Label weatherLabel;
    @FXML private Label statusLabel;
    @FXML private DatePicker startDate;
    @FXML private DatePicker endDate;
    @FXML private ChoiceBox<String> intentChoice;
    @FXML private TextArea itineraryText;

    public DestinationDetailsController(AppNavigator navigator, DestinationService destinationService, ItineraryService itineraryService) {
        this.navigator = navigator;
        this.destinationService = destinationService;
        this.itineraryService = itineraryService;
    }

    @FXML private void initialize() {
        startDate.setValue(LocalDate.now().plusDays(1));
        endDate.setValue(LocalDate.now().plusDays(2));
        intentChoice.getItems().addAll("Peace & nature", "Party & nightlife", "Historical", "Religious", "Beach", "Explore anything");
        intentChoice.setValue("Explore anything");
    }
    public void setDestination(DestinationAssessment destination) {
        this.destination = destination;
        destinationName.setText(destination.place().name());
        airLabel.setText("AQI " + Math.round(destination.air().aqi()) + "  ·  PM2.5 " + String.format("%.1f", destination.air().pm25()));
        weatherLabel.setText(String.format("Now %.0f°C · %.0f%% rain", destination.weather().temperatureCelsius(), destination.weather().rainProbability()));
    }
    @FXML private void generateItinerary() {
        if (endDate.getValue().isBefore(startDate.getValue())) { statusLabel.setText("End date must be after start date."); return; }
        statusLabel.setText("Building your itinerary…"); itineraryText.setText("");
        CompletableFuture.supplyAsync(() -> {
            TripWeather weather = destinationService.tripWeather(destination.place(), startDate.getValue(), endDate.getValue());
            return itineraryService.create(destination, startDate.getValue(), endDate.getValue(), intentChoice.getValue(), weather);
        }).whenComplete((result, error) -> Platform.runLater(() -> showResult(result, error)));
    }
    private void showResult(ItineraryResult result, Throwable error) {
        if (error != null) { statusLabel.setText("Could not create itinerary. Try dates inside the forecast range."); return; }
        itineraryText.setText(result.content()); statusLabel.setText("Generated using " + result.source() + ".");
    }
    @FXML private void goBack() { navigator.showMain(); }
}
