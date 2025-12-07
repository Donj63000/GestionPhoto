package org.example.app;

import java.io.IOException;
import java.net.URL;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main extends Application {
  private static final Logger log = LoggerFactory.getLogger(Main.class);

  public static void main(String[] args) {
    launch(args);
  }

  public static void launchApp(String[] args) {
    launch(args);
  }

  @Override
  public void start(Stage stage) {
    LoggingSetup.prepare();
    Thread.setDefaultUncaughtExceptionHandler(
        (thread, throwable) ->
            log.error("Exception non capturee sur {}", thread.getName(), throwable));

    Scene scene = new Scene(loadRoot());
    applyTheme(scene);

    stage.setTitle("GestionPhoto");
    stage.setScene(scene);
    stage.show();
    log.info("Scene principale chargee et affichee");
  }

  private void applyTheme(Scene scene) {
    URL themeUrl = getClass().getResource("/ui/theme.css");
    if (themeUrl != null) {
      scene.getStylesheets().add(themeUrl.toExternalForm());
    } else {
      log.warn("Feuille de style ui/theme.css introuvable");
    }
  }

  private javafx.scene.Parent loadRoot() {
    FXMLLoader loader = new FXMLLoader(getClass().getResource("/ui/main-view.fxml"));
    try {
      return loader.load();
    } catch (IOException e) {
      log.error("Impossible de charger la vue principale FXML", e);
      throw new IllegalStateException("Echec au chargement de l'interface principale", e);
    }
  }
}
