package com.ineedair.ui;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ineedair.model.DestinationAssessment;
import com.ineedair.model.Place;
import com.ineedair.model.PlaceGuide;
import com.ineedair.model.TravelMood;
import com.ineedair.service.DestinationService;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import netscape.javascript.JSObject;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
public class MainController {
    private final DestinationService destinationService;
    private final ObjectMapper objectMapper;
    private final AppNavigator navigator;

    @FXML private TextField locationSearch;
    @FXML private ChoiceBox<String> radiusChoice;
    @FXML private ChoiceBox<String> moodChoice;
    @FXML private WebView mapView;
    @FXML private ListView<String> resultsList;
    @FXML private Label locationName;
    @FXML private Label aqiValue;
    @FXML private Label pm25Value;
    @FXML private Label weatherValue;
    @FXML private Label scoreValue;
    @FXML private Label statusLabel;
    @FXML private ProgressIndicator loadingIndicator;
    @FXML private ListView<String> leaderboardList;
    @FXML private ListView<String> favouritesList;

    private Place selectedOrigin;
    private List<DestinationAssessment> leaderboard = List.of();
    private DestinationAssessment displayedAssessment;
    private List<com.ineedair.model.FavouritePlace> favourites = List.of();

    public MainController(DestinationService destinationService, ObjectMapper objectMapper, AppNavigator navigator) {
        this.destinationService = destinationService;
        this.objectMapper = objectMapper;
        this.navigator = navigator;
    }

    @FXML
    private void initialize() {
        radiusChoice.setItems(FXCollections.observableArrayList("25 km", "50 km", "100 km", "250 km", "500 km", "No limit"));
        radiusChoice.setValue("100 km");
        moodChoice.setItems(FXCollections.observableArrayList(java.util.Arrays.stream(TravelMood.values()).map(TravelMood::label).toList()));
        moodChoice.setValue(TravelMood.PEACE.label());
        moodChoice.setOnAction(event -> {
            if (selectedOrigin != null) {
                loadLeaderboard(selectedOrigin);
            }
        });
        radiusChoice.setOnAction(event -> {
            if (selectedOrigin != null) {
                loadLeaderboard(selectedOrigin);
            }
        });
        loadingIndicator.setVisible(false);
        refreshFavourites();
        Platform.runLater(() -> {
            WebEngine engine = mapView.getEngine();
            engine.getLoadWorker().stateProperty().addListener((observable, oldState, state) -> {
                if (state == Worker.State.SUCCEEDED) {
                    ((JSObject) engine.executeScript("window")).setMember("javaBridge", this);
                }
            });
            engine.load(getClass().getResource("/com/ineedair/ui/map.html").toExternalForm());
        });
    }

    @FXML
    private void search() {
        String query = locationSearch.getText().trim();
        if (query.isBlank()) {
            statusLabel.setText("Enter a city, address, or place to search.");
            return;
        }
        setLoading(true, "Searching places…");
        CompletableFuture.supplyAsync(() -> destinationService.search(query))
                .whenComplete((places, error) -> Platform.runLater(() -> {
                    if (error != null) {
                        setLoading(false, "Search failed. Check your connection and try again.");
                        return;
                    }
                    showPlaces(places);
                    setLoading(false, places.isEmpty() ? "No matching place found." : "Select your starting place to create a leaderboard.");
                }));
    }

    private void showPlaces(List<Place> places) {
        resultsList.setItems(FXCollections.observableArrayList(places.stream().map(Place::name).toList()));
        resultsList.setOnMouseClicked(event -> {
            int index = resultsList.getSelectionModel().getSelectedIndex();
            if (index >= 0) {
                selectOrigin(places.get(index));
            }
        });
        if (!places.isEmpty()) {
            WebEngine engine = mapView.getEngine();
            engine.executeScript("showSearchResults(" + places.stream().map(this::mapObject).reduce((a, b) -> a + "," + b).orElse("") + ");");
        }
    }

    private void selectOrigin(Place origin) {
        selectedOrigin = origin;
        locationName.setText(origin.name());
        mapView.getEngine().executeScript("showOrigin(" + mapObject(origin) + ");");
        loadLeaderboard(origin);
    }

    private void loadLeaderboard(Place origin) {
        int radiusKilometres = selectedRadius();
        setLoading(true, "Comparing nearby destinations within " + radiusChoice.getValue() + "…");
        CompletableFuture.supplyAsync(() -> destinationService.bestDestinationsNear(origin, radiusKilometres, selectedMood()))
                .whenComplete((assessments, error) -> Platform.runLater(() -> {
                    if (error != null) {
                        setLoading(false, "Could not discover nearby destinations. Please try again.");
                        return;
                    }
                    leaderboard = assessments;
                    leaderboardList.setItems(FXCollections.observableArrayList(
                            assessments.stream().map(this::leaderboardLabel).toList()));
                    leaderboardList.setOnMouseClicked(event -> {
                        int index = leaderboardList.getSelectionModel().getSelectedIndex();
                        if (index >= 0) {
                            updateAssessment(leaderboard.get(index));
                            Place place = leaderboard.get(index).place();
                            selectDestination(place.name(), place.latitude(), place.longitude());
                        }
                    });
                    mapView.getEngine().executeScript("showLeaderboard(" + assessments.stream()
                            .map(assessment -> mapObject(assessment.place()))
                            .reduce((first, second) -> first + "," + second).orElse("") + ");");
                    setLoading(false, assessments.isEmpty()
                            ? "No nearby towns were found in this range. Increase the limit and try again."
                            : "Ranked " + assessments.size() + " " + moodChoice.getValue() + " places. Click a row to focus it on the map.");
                }));
    }

    private int selectedRadius() {
        String value = radiusChoice.getValue();
        return "No limit".equals(value) ? 500 : Integer.parseInt(value.replace(" km", ""));
    }

    private TravelMood selectedMood() {
        return java.util.Arrays.stream(TravelMood.values())
                .filter(mood -> mood.label().equals(moodChoice.getValue())).findFirst().orElse(TravelMood.EXPLORE);
    }

    @FXML
    private void beginLocationPick() {
        statusLabel.setText("Click anywhere on the map to set your current location.");
        mapView.getEngine().executeScript("enableLocationPick();");
    }

    /** Invoked by the map after the user chooses a new current location. */
    public void chooseCurrentLocation(double latitude, double longitude) {
        setLoading(true, "Setting your current location…");
        CompletableFuture.supplyAsync(() -> destinationService.currentLocation(latitude, longitude))
                .whenComplete((place, error) -> Platform.runLater(() -> {
                    if (error != null) {
                        setLoading(false, "Could not identify that map location.");
                        return;
                    }
                    selectOrigin(place);
                }));
    }

    private String leaderboardLabel(DestinationAssessment assessment) {
        int rank = leaderboard.indexOf(assessment) + 1;
        return String.format("#%d  %s%n     AQI %.0f  ·  Score %d/100", rank, assessment.place().name(), assessment.air().aqi(), assessment.healthScore());
    }

    private void loadAssessment(Place place) {
        setLoading(true, "Checking live air quality and weather…");
        CompletableFuture.supplyAsync(() -> destinationService.assess(place))
                .whenComplete((assessment, error) -> Platform.runLater(() -> {
                    if (error != null) {
                        setLoading(false, "Live data is temporarily unavailable. Please try again.");
                        return;
                    }
                    updateAssessment(assessment);
                    setLoading(false, "Live data updated. Route comparison will be added next.");
                }));
    }

    private void updateAssessment(DestinationAssessment assessment) {
        displayedAssessment = assessment;
        locationName.setText(assessment.place().name());
        aqiValue.setText(String.format("%.0f", assessment.air().aqi()));
        pm25Value.setText(String.format("%.1f µg/m³", assessment.air().pm25()));
        weatherValue.setText(String.format("%.0f°C  ·  %.0f%% rain", assessment.weather().temperatureCelsius(), assessment.weather().rainProbability()));
        scoreValue.setText(assessment.healthScore() + " / 100");
        mapView.getEngine().executeScript("focusPlace(" + mapObject(assessment.place()) + ");");
    }

    @FXML
    private void saveFavourite() {
        if (displayedAssessment == null) {
            statusLabel.setText("Choose a recommended destination before saving it.");
            return;
        }
        destinationService.saveFavourite(displayedAssessment.place());
        refreshFavourites();
        statusLabel.setText(displayedAssessment.place().name() + " saved to favourites.");
    }

    private void refreshFavourites() {
        favourites = destinationService.favourites();
        favouritesList.setItems(FXCollections.observableArrayList(favourites.stream().map(favourite -> favourite.place().name()).toList()));
        favouritesList.setOnMouseClicked(event -> {
            int index = favouritesList.getSelectionModel().getSelectedIndex();
            if (index >= 0) {
                loadAssessment(favourites.get(index).place());
                Place place = favourites.get(index).place();
                selectDestination(place.name(), place.latitude(), place.longitude());
            }
        });
    }

    /** Invoked by the embedded map when the user clicks a destination marker. */
    public void selectDestination(String name, double latitude, double longitude) {
        CompletableFuture.supplyAsync(() -> destinationService.placeGuide(new Place(name, latitude, longitude)))
                .whenComplete((guide, error) -> Platform.runLater(() -> {
                    if (error == null) {
                        showPlaceGuide(guide);
                    }
                }));
    }

    private void showPlaceGuide(PlaceGuide guide) {
        try {
            mapView.getEngine().executeScript("showPlaceGuide(" + objectMapper.writeValueAsString(guide) + ");");
        } catch (JsonProcessingException exception) {
            statusLabel.setText("Could not display the destination guide.");
        }
    }

    @FXML
    private void openTripPlanner() {
        if (displayedAssessment == null) {
            statusLabel.setText("Choose a destination first.");
            return;
        }
        navigator.showDetails(displayedAssessment);
    }

    private String mapObject(Place place) {
        return "{name:" + quote(place.name()) + ",lat:" + place.latitude() + ",lon:" + place.longitude() + "}";
    }

    private String quote(String text) {
        return "'" + text.replace("\\", "\\\\").replace("'", "\\'").replace("\n", " ") + "'";
    }

    private void setLoading(boolean loading, String message) {
        loadingIndicator.setVisible(loading);
        statusLabel.setText(message);
    }
}
