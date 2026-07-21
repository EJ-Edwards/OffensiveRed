package com.redsecai;

import com.redsecai.controller.MainController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Entry point for the RedSecAI adversary-emulation GUI.
 */
public class Main extends Application {

    private MainController controller;

    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main_view.fxml"));
        Parent root = loader.load();
        controller = loader.getController();

        Scene scene = new Scene(root, 1200, 820);
        scene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());

        primaryStage.setTitle("RedSecAI — Adversary Emulation");
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(1040);
        primaryStage.setMinHeight(720);
        primaryStage.show();
    }

    @Override
    public void stop() {
        if (controller != null) {
            controller.shutdown();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
