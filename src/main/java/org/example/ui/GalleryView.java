package org.example.ui;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;
import org.example.core.Photo;
import org.example.core.ScanService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GalleryView {
  private static final Logger log = LoggerFactory.getLogger(GalleryView.class);

  private final BorderPane root;
  private final ScanService scanService;
  private final TilePane grid;
  private final Label statusLabel;
  private final ProgressIndicator progressIndicator;

  public GalleryView(ScanService scanService) {
    this.scanService = scanService;
    this.root = new BorderPane();
    this.grid = new TilePane(12, 12);
    this.statusLabel = new Label("Aucune photo importee");
    this.progressIndicator = new ProgressIndicator();
    progressIndicator.setVisible(false);

    root.setTop(buildHeader());
    root.setCenter(buildContent());
  }

  public BorderPane getRoot() {
    return root;
  }

  private Node buildHeader() {
    HBox header = new HBox(12);
    header.setAlignment(Pos.CENTER_LEFT);
    header.setPadding(new Insets(16));

    Label title = new Label("Photos Gestion - Import");
    Button importButton = new Button("Importer");
    importButton.setOnAction(event -> launchImport(importButton.getScene().getWindow()));

    header.getChildren().addAll(title, importButton, statusLabel, progressIndicator);
    return header;
  }

  private Node buildContent() {
    grid.setPrefColumns(4);
    grid.setPadding(new Insets(16));
    ScrollPane scrollPane = new ScrollPane(grid);
    scrollPane.setFitToWidth(true);
    return scrollPane;
  }

  private void launchImport(Window owner) {
    DirectoryChooser chooser = new DirectoryChooser();
    chooser.setTitle("Choisir un dossier a scanner");
    File selected = chooser.showDialog(owner);
    if (selected == null) {
      log.info("Import annule par l'utilisateur");
      return;
    }
    Path path = selected.toPath();
    statusLabel.setText("Scan en cours : " + path);
    progressIndicator.setVisible(true);
    Task<List<Photo>> scanTask =
        new Task<>() {
          @Override
          protected List<Photo> call() {
            return scanService.scan(path);
          }
        };
    scanTask.setOnSucceeded(
        event -> {
          List<Photo> photos = scanTask.getValue();
          renderPhotos(photos);
          statusLabel.setText(
              photos.isEmpty() ? "Aucune photo trouvee" : photos.size() + " photos chargees");
          progressIndicator.setVisible(false);
        });
    scanTask.setOnFailed(
        event -> {
          Throwable error = scanTask.getException();
          log.error("Echec lors de l'import", error);
          progressIndicator.setVisible(false);
          statusLabel.setText("Echec du scan");
          String message =
              "Le dossier n'a pas pu etre scanne. Verifiez le disque et reessayez.";
          if (error != null && error.getMessage() != null && !error.getMessage().isBlank()) {
            message += " Details : " + error.getMessage();
          }
          showError("Import echoue", message);
        });
    Thread worker = new Thread(scanTask, "scan-worker");
    worker.setDaemon(true);
    worker.start();
  }

  private void renderPhotos(List<Photo> photos) {
    grid.getChildren().clear();
    for (Photo photo : photos) {
      grid.getChildren().add(createCard(photo));
    }
  }

  private Node createCard(Photo photo) {
    Rectangle placeholder = new Rectangle(140, 100, Color.LIGHTGRAY);
    placeholder.setArcWidth(12);
    placeholder.setArcHeight(12);

    Label name = new Label(photo.fileName());
    name.setMaxWidth(140);
    name.setWrapText(true);

    Label meta = new Label(String.format("%d Ko", photo.sizeBytes() / 1024));
    VBox content = new VBox(6, placeholder, name, meta);
    content.setPadding(new Insets(8));
    content.setAlignment(Pos.CENTER_LEFT);

    StackPane card = new StackPane(content);
    card.getStyleClass().add("photo-card");
    return card;
  }

  private void showError(String title, String message) {
    Alert alert = new Alert(Alert.AlertType.ERROR);
    alert.setTitle(title);
    alert.setHeaderText(null);
    alert.setContentText(message);
    alert.showAndWait();
  }
}
