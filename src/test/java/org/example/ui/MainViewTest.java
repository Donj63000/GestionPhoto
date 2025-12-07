package org.example.ui;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.TextField;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;
import org.example.infra.PhotoFileScanner;
import org.example.infra.ThumbnailService;
import org.example.ui.MainView.AlbumSelection;
import org.example.ui.model.PhotoItem;
import org.example.ui.service.PhotoLibraryService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
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
    void shouldFilterAlbums() {
        MainView view = new MainView();
        TilePane grid = (TilePane) ((VBox) ((ScrollPane) view.getRoot().getCenter())
                .getContent()).getChildren().get(1).lookup(".gallery-grid");
        int total = grid.getChildren().size();

        view.getRoot().lookupAll(".filter-chip").stream()
                .filter(node -> node instanceof ToggleButton)
                .map(node -> (ToggleButton) node)
                .filter(btn -> "Albums".equals(btn.getText()))
                .findFirst()
                .ifPresent(ToggleButton::fire);

        int albumsCount = grid.getChildren().size();
        assertTrue(albumsCount > 0, "Albums grid should display items");
        assertTrue(albumsCount < total, "Albums filter should reduce grid to album-backed photos");
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
    void shouldCreateAlbumFromDialogAndRefreshGrid() throws Exception {
        PhotoLibraryService service = new PhotoLibraryService();
        List<PhotoItem> selection = service.filter("", PhotoLibraryService.Filter.ALL)
                .subList(0, Math.min(2, service.all().size()));
        AlbumSelection albumSelection = new AlbumSelection("Album Test", selection);

        Dialog<AlbumSelection> dialog = new Dialog<>() {
            @Override
            public java.util.Optional<AlbumSelection> showAndWait() {
                return java.util.Optional.of(albumSelection);
            }
        };

        AlbumTestView view = new AlbumTestView(service, dialog);
        Button createAlbum = findButton(view.getRoot(), "Creer un album", ".secondary-button");
        runOnFxThread(createAlbum::fire);

        assertEquals("Album 'Album Test' cree", findStatusLabel(view.getRoot()).getText());

        Button albumsNav = findButton(view.getRoot(), "Albums", ".nav-button");
        runOnFxThread(albumsNav::fire);

        runOnFxThread(() -> ((TextField) view.getRoot().lookup(".search-field")).setText("Album Test"));

        TilePane grid = (TilePane) ((VBox) ((ScrollPane) view.getRoot().getCenter())
                .getContent()).getChildren().get(1).lookup(".gallery-grid");
        assertTrue(grid.getChildren().size() >= selection.size(), "Grid should show newly album-tagged photos");
    }

    @Test
    void shouldShowExportPreparationMessage() throws Exception {
        MainView view = new MainView();
        Button exportButton = findButton(view.getRoot(), "Exporter", ".secondary-button");

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

    private static class AlbumTestView extends MainView {
        private final Dialog<AlbumSelection> dialog;

        AlbumTestView(PhotoLibraryService service, Dialog<AlbumSelection> dialog) {
            super(service, new PhotoFileScanner(), new ThumbnailService());
            this.dialog = dialog;
        }

        @Override
        protected Dialog<AlbumSelection> buildAlbumDialog(Window owner, List<PhotoItem> activePhotos) {
            return dialog;
        }
    }
}
