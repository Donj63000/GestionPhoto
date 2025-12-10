package org.example.app;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.example.infra.ExportService;
import org.example.infra.PhotoFileScanner;
import org.example.infra.ThumbnailService;
import org.example.ui.MainView;
import org.example.ui.service.PhotoLibraryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PhotoGestionApp extends Application {
  private static final Logger log = LoggerFactory.getLogger(PhotoGestionApp.class);
  private MainView mainView;

  public static void launchApp(String[] args) {
    launch(args);
  }

  @Override
  public void start(Stage stage) {
    LoggingSetup.prepare();
    Thread.setDefaultUncaughtExceptionHandler(
        (thread, throwable) ->
            log.error("Exception non capturee sur {}", thread.getName(), throwable));

    mainView =
        new MainView(
            new PhotoLibraryService(),
            new PhotoFileScanner(),
            new ThumbnailService(),
            new ExportService());

    Scene scene = new Scene(mainView.getRoot(), 1200, 800);

    String theme = "/ui/theme.css";
    var themeUrl = getClass().getResource(theme);
    if (themeUrl != null) {
      scene.getStylesheets().add(themeUrl.toExternalForm());
    } else {
      log.warn("Feuille de style {} introuvable", theme);
    }

    stage.setTitle("Photos Gestion");
    stage.setScene(scene);
    stage.setMinWidth(960);
    stage.setMinHeight(640);

    stage.show();

    log.info("UI principale initialisee (MainView)");
  }

  @Override
  public void stop() {
    if (mainView != null) {
      mainView.shutdown();
    }
  }
}
