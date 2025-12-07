package org.example.app;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.example.ui.MainView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;

public class PhotoGestionApp extends Application {
    private static final Logger log = LoggerFactory.getLogger(PhotoGestionApp.class);

    public static void launchApp(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        Thread.setDefaultUncaughtExceptionHandler(
                (thread, throwable) -> log.error("Exception non capturee sur {}", thread.getName(), throwable));

        MainView mainView = new MainView();
        Scene scene = new Scene(mainView.getRoot(), 1200, 780);

        URL themeUrl = getClass().getResource("/ui/theme.css");
        if (themeUrl != null) {
            scene.getStylesheets().add(themeUrl.toExternalForm());
        } else {
            log.warn("Feuille de style ui/theme.css introuvable");
        }

        stage.setTitle("Photos Gestion - Accueil");
        stage.setScene(scene);
        stage.setMinWidth(960);
        stage.setMinHeight(640);
        stage.show();

        log.info("UI principale initialisee");
    }
}
