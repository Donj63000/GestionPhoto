package org.example.ui;

import javafx.application.Platform;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainViewTest {
    private static final AtomicBoolean jfxStarted = new AtomicBoolean(false);

    @BeforeAll
    static void setupToolkit() throws Exception {
        if (jfxStarted.compareAndSet(false, true)) {
            CountDownLatch latch = new CountDownLatch(1);
            Platform.startup(latch::countDown);
            latch.await();
        }
    }

    @Test
    void shouldBuildRootWithCssClass() {
        MainView view = new MainView();
        assertNotNull(view.getRoot(), "Root pane should not be null");
        assertTrue(view.getRoot().getStyleClass().contains("app-root"),
                "Root pane should carry the app-root class for styling");
    }

    @Test
    void shouldRenderGridWithMockData() {
        MainView view = new MainView();
        // grid is the center scroll -> content VBox -> grid wrapper VBox -> TilePane
        TilePane grid = (TilePane) ((VBox) ((ScrollPane) view.getRoot().getCenter())
                .getContent()).getChildren().get(1).lookup(".gallery-grid");
        assertNotNull(grid, "Grid should be present");
        assertTrue(grid.getChildren().size() >= 1, "Grid should have items");
    }

    @Test
    void shouldFilterFavorites() {
        MainView view = new MainView();
        view.getRoot().lookupAll(".filter-chip").stream()
                .filter(node -> node instanceof ToggleButton)
                .map(node -> (ToggleButton) node)
                .filter(btn -> "Favoris".equals(btn.getText()))
                .findFirst()
                .ifPresent(btn -> btn.fire());

        TilePane grid = (TilePane) ((VBox) ((ScrollPane) view.getRoot().getCenter())
                .getContent()).getChildren().get(1).lookup(".gallery-grid");

        assertTrue(grid.getChildren().size() >= 1, "Favorites grid should not be empty");

        // crude check: favorites should be fewer or equal to total (8 in seed)
        assertEquals(true, grid.getChildren().size() <= 8);
    }
}
