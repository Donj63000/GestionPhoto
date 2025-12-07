package org.example.ui;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;
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

    @Test
    void shouldTriggerImportFromSidebarButton() throws Exception {
        AtomicBoolean chooserCalled = new AtomicBoolean(false);
        DirectoryChooser chooser = new DirectoryChooser() {
            @Override
            public java.io.File showDialog(Window ownerWindow) {
                chooserCalled.set(true);
                return null; // simulate cancel
            }
        };
        TestableMainView view = new TestableMainView(chooser);

        Button importButton = findButton(view.getRoot(), "Importer", ".secondary-button");
        runOnFxThread(importButton::fire);

        assertTrue(chooserCalled.get(), "Import chooser should be opened");
        assertEquals("Import annule", findStatusLabel(view.getRoot()).getText());
    }

    @Test
    void shouldNavigateWithSidebarButtons() throws Exception {
        MainView view = new MainView();
        Button favoritesNav = findButton(view.getRoot(), "Favoris", ".nav-button");
        runOnFxThread(favoritesNav::fire);

        assertEquals("Favoris", getSelectedFilterLabel(view));

        Button todoNav = findButton(view.getRoot(), "A trier", ".nav-button");
        runOnFxThread(todoNav::fire);

        assertEquals("Recents", getSelectedFilterLabel(view));
    }

    @Test
    void shouldShowDialogsForAlbumAndExportActions() throws Exception {
        MainView view = new MainView();
        Button createAlbum = findButton(view.getRoot(), "Creer un album", ".secondary-button");
        Button exportButton = findButton(view.getRoot(), "Exporter", ".secondary-button");

        runOnFxThread(createAlbum::fire);
        assertEquals("Creation d'album en preparation", findStatusLabel(view.getRoot()).getText());

        runOnFxThread(exportButton::fire);
        assertEquals("Export en preparation", findStatusLabel(view.getRoot()).getText());
    }

    private static Button findButton(Node root, String label, String cssClass) {
        return root.lookupAll(cssClass).stream()
                .filter(node -> node instanceof Button)
                .map(node -> (Button) node)
                .filter(btn -> label.equals(btn.getText()))
                .findFirst()
                .orElseThrow();
    }

    private static Label findStatusLabel(Node root) {
        return (Label) root.lookup(".status-label");
    }

    private static String getSelectedFilterLabel(MainView view) {
        return view.getRoot().lookupAll(".filter-chip").stream()
                .filter(node -> node instanceof ToggleButton)
                .map(node -> (ToggleButton) node)
                .filter(ToggleButton::isSelected)
                .map(ToggleButton::getText)
                .findFirst()
                .orElse("");
    }

    private static void runOnFxThread(Runnable action) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            action.run();
            latch.countDown();
        });
        latch.await();
    }

    private static class TestableMainView extends MainView {
        private final DirectoryChooser chooser;

        TestableMainView(DirectoryChooser chooser) {
            super();
            this.chooser = chooser;
        }

        @Override
        protected DirectoryChooser createDirectoryChooser() {
            return chooser;
        }
    }
}
