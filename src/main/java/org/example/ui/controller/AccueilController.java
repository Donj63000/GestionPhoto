package org.example.ui.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AccueilController {
  private static final Logger log = LoggerFactory.getLogger(AccueilController.class);

  @FXML private Label statusLabel;

  @FXML
  public void initialize() {
    log.info("Controleur Accueil initialise");
  }

  @FXML
  public void openParcours(ActionEvent event) {
    log.info("Action Parcours declenchee depuis l'accueil: {}", event.getEventType());
    if (statusLabel != null) {
      statusLabel.setText("Navigation vers le parcours (placeholder)");
    }
  }
}
