package org.example.app;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PhotoGestionAppLifecycleTest {
  private static final AtomicBoolean jfxStarted = new AtomicBoolean(false);

  @BeforeAll
  static void setupToolkit() throws Exception {
    if (jfxStarted.compareAndSet(false, true)) {
      System.setProperty("javafx.platform", "Monocle");
      System.setProperty("glass.platform", "Monocle");
      System.setProperty("monocle.platform", "Headless");
      System.setProperty("monocle.screen", "offscreen");
      System.setProperty("javafx.headless", "true");
      System.setProperty("prism.order", "sw");
      System.setProperty("prism.text", "t2k");
      System.setProperty("java.awt.headless", "true");
      CountDownLatch latch = new CountDownLatch(1);
      try {
        Platform.startup(latch::countDown);
        boolean started = latch.await(5, TimeUnit.SECONDS);
        Assumptions.assumeTrue(started, "Initialisation JavaFX indisponible");
      } catch (Exception | Error e) {
        jfxStarted.set(false);
        Assumptions.assumeTrue(false, "JavaFX non disponible: " + e.getMessage());
      }
    }
  }

  @Test
  void shouldShutdownThumbnailExecutorWhenStageCloses() throws Exception {
    CountDownLatch stageClosed = new CountDownLatch(1);
    AtomicReference<PhotoGestionApp> appRef = new AtomicReference<>();

    Platform.runLater(
        () -> {
          PhotoGestionApp app = new PhotoGestionApp();
          appRef.set(app);
          Stage stage = new Stage();
          try {
            app.start(stage);
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
          stage.setOnHidden(event -> stageClosed.countDown());
          stage.fireEvent(new WindowEvent(stage, WindowEvent.WINDOW_CLOSE_REQUEST));
        });

    assertTrue(
        stageClosed.await(5, TimeUnit.SECONDS), "La fermeture de la fenetre doit etre traitee");

    PhotoGestionApp app = appRef.get();
    if (app != null) {
      app.stop();
    }

    assertTrue(
        waitForNoThumbnailThreads(2, TimeUnit.SECONDS),
        "Le pool de miniatures doit etre arrete apres fermeture du stage");
  }

  private boolean waitForNoThumbnailThreads(long timeout, TimeUnit unit)
      throws InterruptedException {
    long deadline = System.nanoTime() + unit.toNanos(timeout);
    while (System.nanoTime() < deadline) {
      boolean none =
          Thread.getAllStackTraces().keySet().stream()
              .noneMatch(t -> t.isAlive() && t.getName().startsWith("thumbnail-loader-"));
      if (none) {
        return true;
      }
      Thread.sleep(50);
    }
    return false;
  }
}
