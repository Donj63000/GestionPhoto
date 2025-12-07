package org.example.app;

import java.net.URL;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.example.core.ScanService;
import org.example.infra.FileSystemGateway;
import org.example.ui.GalleryView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PhotoGestionApp extends Application {
  private static final Logger log = LoggerFactory.getLogger(PhotoGestionApp.class);
  private GalleryView galleryView;

  public static void launchApp(String[] args) {
    launch(args);
  }

  @Override
  public void start(Stage stage) {
    Thread.setDefaultUncaughtExceptionHandler(
        (thread, throwable) ->
            log.error("Exception non capturee sur {}", thread.getName(), throwable));

    FileSystemGateway gateway = new FileSystemGateway();
    ScanService scanService = new ScanService(gateway);
    galleryView = new GalleryView(scanService);
    Scene scene = new Scene(galleryView.getRoot(), 960, 640);

    URL themeUrl = getClass().getResource("/ui/theme.css");
    if (themeUrl != null) {
      scene.getStylesheets().add(themeUrl.toExternalForm());
    } else {
      log.warn("Feuille de style ui/theme.css introuvable");
    }

    stage.setTitle("Photos Gestion - Import");
    stage.setScene(scene);
    stage.setMinWidth(800);
    stage.setMinHeight(560);

    stage.show();

    log.info("UI principale initialisee");
  }

  @Override
  public void stop() {
    // Rien a liberer pour le moment
  }
}
