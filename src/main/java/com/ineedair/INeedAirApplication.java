package com.ineedair;

import com.ineedair.ui.INeedAirDesktop;
import javafx.application.Application;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class INeedAirApplication {
    public static void main(String[] args) {
        Application.launch(INeedAirDesktop.class, args);
    }
}
