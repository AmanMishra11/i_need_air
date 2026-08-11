package com.ineedair.ui;

import com.ineedair.INeedAirApplication;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.IOException;

public class INeedAirDesktop extends Application {
    private ConfigurableApplicationContext springContext;

    @Override
    public void init() {
        springContext = new SpringApplicationBuilder(INeedAirApplication.class)
                .headless(false)
                .web(WebApplicationType.NONE)
                .run();
    }

    @Override
    public void start(Stage stage) throws IOException {
        stage.setMinWidth(1100);
        stage.setMinHeight(680);
        springContext.getBean(AppNavigator.class).setStage(stage);
        springContext.getBean(AppNavigator.class).showMain();
        stage.show();
    }

    @Override
    public void stop() {
        springContext.close();
        Platform.exit();
    }
}
