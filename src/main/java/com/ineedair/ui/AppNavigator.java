package com.ineedair.ui;

import com.ineedair.model.DestinationAssessment;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class AppNavigator {
    private final ConfigurableApplicationContext context;
    private Stage stage;

    public AppNavigator(ConfigurableApplicationContext context) {
        this.context = context;
    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public void showMain() {
        show("/com/ineedair/ui/main-view.fxml", "I Need Air");
    }

    public void showDetails(DestinationAssessment assessment) {
        try {
            FXMLLoader loader = loader("/com/ineedair/ui/destination-details.fxml");
            Scene scene = new Scene(loader.load(), 1220, 800);
            scene.getStylesheets().add(getClass().getResource("/com/ineedair/ui/app.css").toExternalForm());
            ((DestinationDetailsController) loader.getController()).setDestination(assessment);
            stage.setScene(scene);
            stage.setTitle("I Need Air — Trip Planner");
        } catch (IOException exception) {
            throw new IllegalStateException("Could not open the trip planner.", exception);
        }
    }

    private void show(String view, String title) {
        try {
            FXMLLoader loader = loader(view);
            Scene scene = new Scene(loader.load(), 1360, 820);
            scene.getStylesheets().add(getClass().getResource("/com/ineedair/ui/app.css").toExternalForm());
            stage.setScene(scene);
            stage.setTitle(title);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not open the application screen.", exception);
        }
    }

    private FXMLLoader loader(String view) {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(view));
        loader.setControllerFactory(context::getBean);
        return loader;
    }
}
