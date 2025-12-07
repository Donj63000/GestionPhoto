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
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import org.example.infra.ExportService;
import org.example.infra.PhotoFileScanner;
import org.example.infra.ThumbnailService;
import org.example.ui.MainView.AlbumSelection;
import org.example.ui.model.PhotoItem;
import org.example.ui.service.PhotoLibraryService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainViewTest {
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
    void shouldBuildRootWithCssClass() {
        MainView view = new MainView();
        assertNotNull(view.getRoot(), "Root pane should not be null");
        assertTrue(view.getRoot().getStyleClass().contains("app-root"),
                "Root pane should carry the app-root class for styling");
    }

    @Test
    void shouldShowEmptyStateWhenNoPhotos() {
        MainView view = new MainView();
        // grid is the center scroll -> content VBox -> grid wrapper VBox -> TilePane
        TilePane grid = (TilePane) ((VBox) ((ScrollPane) view.getRoot().getCenter())
                .getContent()).getChildren().get(1).lookup(".gallery-grid");
        assertNotNull(grid, "Grid should be present");
        assertEquals(1, grid.getChildren().size(), "Grid should show an empty state placeholder");
        Node placeholder = grid.getChildren().get(0);
        assertTrue(placeholder.getStyleClass().contains("empty-state"), "Empty state should be styled");
        Label title = (Label) ((VBox) placeholder).getChildren().get(0);
        assertEquals("Aucune photo importee pour le moment", title.getText());
        assertEquals("Aucune photo importee. Lancez un scan ou importez un dossier.", findStatusLabel(view.getRoot()).getText());
    }

    @Test
    void shouldFilterFavorites() {
        PhotoLibraryService service = serviceWithDemoData();
        MainView view = new MainView(service, new PhotoFileScanner(), new ThumbnailService(), new ExportService());
        view.getRoot().lookupAll(".filter-chip").stream()
                .filter(node -> node instanceof ToggleButton)
                .map(node -> (ToggleButton) node)
                .filter(btn -> "Favoris".equals(btn.getText()))
                .findFirst()
                .ifPresent(btn -> btn.fire());

        TilePane grid = (TilePane) ((VBox) ((ScrollPane) view.getRoot().getCenter())
                .getContent()).getChildren().get(1).lookup(".gallery-grid");

        assertTrue(grid.getChildren().size() >= 1, "Favorites grid should not be empty");
    }

    @Test
    void shouldFilterAlbums() {
        PhotoLibraryService service = serviceWithDemoData();
        MainView view = new MainView(service, new PhotoFileScanner(), new ThumbnailService(), new ExportService());
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
        TestableMainView view = new TestableMainView(() -> {
            chooserCalled.set(true);
            return null; // simulate cancel
        });

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
    void shouldShowDetailDialogOnCardClick() throws Exception {
        DetailTestView view = new DetailTestView(serviceWithDemoData());
        TilePane grid = extractGrid(view.getRoot());
        VBox card = (VBox) grid.getChildren().get(0);

        runOnFxThread(() -> card.getOnMouseClicked().handle(new MouseEvent(MouseEvent.MOUSE_CLICKED,
                0, 0, 0, 0, MouseButton.PRIMARY, 1,
                false, false, false, false,
                true, false, false, true, false, false, null)));

        assertTrue(view.detailShown.get(), "Detail dialog should be requested on click");
        assertNotNull(view.capturedItem, "Clicked item should be captured for details");
    }

    @Test
    void shouldCreateAlbumFromDialogAndRefreshGrid() throws Exception {
        PhotoLibraryService service = serviceWithDemoData();
        List<PhotoItem> selection = service.filter("", PhotoLibraryService.Filter.ALL)
                .subList(0, Math.min(2, service.all().size()));
        AlbumSelection albumSelection = new AlbumSelection("Album Test", selection);

        Dialog<AlbumSelection> dialog = new Dialog<>();
        dialog.setResult(albumSelection);
        dialog.setOnShowing(event -> Platform.runLater(dialog::close));

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
        AtomicBoolean chooserCalled = new AtomicBoolean(false);
        TestableMainView view = new TestableMainView(() -> {
            chooserCalled.set(true);
            return null;
        });
        Button exportButton = findButton(view.getRoot(), "Exporter", ".secondary-button");

        runOnFxThread(exportButton::fire);
        assertTrue(chooserCalled.get(), "Export chooser should be opened");
        assertEquals("Export annule", findStatusLabel(view.getRoot()).getText());
    }

    @Test
    void shouldToggleFavoriteAndRefreshFilters() throws Exception {
        PhotoLibraryService service = serviceWithDemoData();
        PhotoItem target = service.all().stream()
                .filter(item -> !item.favorite())
                .findFirst()
                .orElseThrow();

        FavoriteTestView view = new FavoriteTestView(service);
        TilePane grid = extractGrid(view.getRoot());
        VBox card = findCardByTitle(grid, target.title());
        Button favoriteButton = (Button) card.lookup(".favorite-toggle");

        runOnFxThread(favoriteButton::fire);

        ToggleButton favoritesFilter = view.getRoot().lookupAll(".filter-chip").stream()
                .filter(node -> node instanceof ToggleButton)
                .map(node -> (ToggleButton) node)
                .filter(btn -> "Favoris".equals(btn.getText()))
                .findFirst()
                .orElseThrow();
        runOnFxThread(favoritesFilter::fire);

        assertTrue(grid.getChildren().stream().anyMatch(node -> containsTitle(node, target.title())),
                "Toggled favorite should appear when favorites filter is active");

        VBox favoriteCard = findCardByTitle(grid, target.title());
        Button toggleBack = (Button) favoriteCard.lookup(".favorite-toggle");
        runOnFxThread(toggleBack::fire);

        assertTrue(grid.getChildren().stream().noneMatch(node -> containsTitle(node, target.title())),
                "Removing favorite while filter is active should hide the card");
    }

    @Test
    void shouldApplyPartialSelectionToNewAlbum() throws Exception {
        PhotoLibraryService service = serviceWithDemoData();
        int initialSize = service.all().size();

        List<PhotoItem> scanned = List.of(
                new PhotoItem(Path.of("imports/nouvelle1.jpg"), "Nouvelle photo", LocalDate.now(), "1.0 MB", List.of(), List.of(), false),
                new PhotoItem(service.all().get(0).path(), "Doublon", LocalDate.now(), "1.0 MB", List.of(), List.of(), false)
        );

        Dialog<MainView.ScanSelection> dialog = new Dialog<>();
        dialog.setResult(new MainView.ScanSelection(List.of(scanned.get(0)), "Album Test FX"));
        dialog.setOnShowing(event -> Platform.runLater(dialog::close));

        ScanSelectionTestView view = new ScanSelectionTestView(service, dialog);
        runOnFxThread(() -> view.handleScanResults(null, new PhotoFileScanner.ScanResult(scanned, List.of())));

        assertEquals(initialSize + 1, service.all().size(), "Only the selected new photo should be added");
        PhotoItem added = service.all().stream()
                .filter(item -> item.path().equals(scanned.get(0).path()))
                .findFirst()
                .orElseThrow();
        assertTrue(added.albums().stream().anyMatch(name -> name.equalsIgnoreCase("Album Test FX")),
                "Chosen album should be applied to the added photo");
        assertEquals("1 photos ajoutees (Album Test FX). 1 doublon(s) ignore(s). Photos visibles dans la grille.",
                findStatusLabel(view.getRoot()).getText());
    }

    @Test
    void shouldRefreshGridAndTitleAfterScanResults() throws Exception {
        PhotoLibraryService service = new PhotoLibraryService();
        List<PhotoItem> scanned = List.of(
                new PhotoItem(Path.of("imports/photo1.jpg"), "Photo 1", LocalDate.now(), "1 MB", List.of(), List.of(), false),
                new PhotoItem(Path.of("imports/photo2.jpg"), "Photo 2", LocalDate.now(), "1 MB", List.of(), List.of(), false)
        );

        Dialog<MainView.ScanSelection> dialog = new Dialog<>();
        dialog.setResult(new MainView.ScanSelection(scanned, ""));
        dialog.setOnShowing(event -> Platform.runLater(dialog::close));

        ScanSelectionTestView view = new ScanSelectionTestView(service, dialog);
        runOnFxThread(() -> view.handleScanResults(null, new PhotoFileScanner.ScanResult(scanned, List.of())));

        TilePane grid = extractGrid(view.getRoot());
        Label gridTitle = findGridTitle(view.getRoot());

        assertEquals(2, grid.getChildren().size(), "Grid should show the scanned items");
        assertTrue(gridTitle.getText().contains("2"), "Grid title should display the scanned count");
        assertTrue(findStatusLabel(view.getRoot()).getText().contains("Photos visibles"),
                "Status label should confirm visibility in the grid");
    }

    static Button findButton(Node root, String label, String cssClass) {
        return root.lookupAll(cssClass).stream()
                .filter(node -> node instanceof Button)
                .map(node -> (Button) node)
                .filter(btn -> label.equals(btn.getText()))
                .findFirst()
                .orElseThrow();
    }

    static Label findStatusLabel(Node root) {
        return (Label) root.lookup(".status-label");
    }

    static Label findGridTitle(Node root) {
        return (Label) root.lookup(".grid-title");
    }

    private static TilePane extractGrid(Node root) {
        return (TilePane) ((VBox) ((ScrollPane) root.lookup(".content-scroll")).getContent())
                .getChildren().get(1).lookup(".gallery-grid");
    }

    private static PhotoLibraryService serviceWithDemoData() {
        PhotoLibraryService service = new PhotoLibraryService();
        service.replaceAll(demoPhotos());
        return service;
    }

    private static List<PhotoItem> demoPhotos() {
        return List.of(
                new PhotoItem(Path.of("demo/plage.jpg"), "Plage estivale", LocalDate.now().minusDays(5), "2.1 MB",
                        List.of("vacances", "famille"), List.of("Vacances", "Ete"), true),
                new PhotoItem(Path.of("demo/anniversaire.jpg"), "Anniversaire", LocalDate.now().minusMonths(1), "3.4 MB",
                        List.of("famille", "gateau"), List.of("Famille"), false),
                new PhotoItem(Path.of("demo/randonnee.jpg"), "Randonnee", LocalDate.now().minusMonths(4), "2.9 MB",
                        List.of("montagne", "sport"), List.of(), false),
                new PhotoItem(Path.of("demo/chat.jpg"), "Chat endormi", LocalDate.now().minusDays(20), "1.2 MB",
                        List.of("animal", "maison"), List.of(), true),
                new PhotoItem(Path.of("demo/concert.jpg"), "Concert", LocalDate.now().minusMonths(2), "4.5 MB",
                        List.of("musique", "amis"), List.of("Sorties"), false),
                new PhotoItem(Path.of("demo/jardin.jpg"), "Jardin fleuri", LocalDate.now().minusDays(15), "2.0 MB",
                        List.of("nature", "couleurs"), List.of(), false),
                new PhotoItem(Path.of("demo/souvenir-ete.jpg"), "Souvenir d'ete", LocalDate.now().minusMonths(6), "3.0 MB",
                        List.of("voyage", "soleil"), List.of("Voyages"), false),
                new PhotoItem(Path.of("demo/noel.jpg"), "Noel", LocalDate.now().minusMonths(11), "5.2 MB",
                        List.of("famille", "fete"), List.of("Famille"), true)
        );
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

    private static VBox findCardByTitle(TilePane grid, String title) {
        return grid.getChildren().stream()
                .filter(node -> node instanceof VBox)
                .map(node -> (VBox) node)
                .filter(node -> containsTitle(node, title))
                .findFirst()
                .orElseThrow();
    }

    private static boolean containsTitle(Node node, String title) {
        AtomicReference<String> found = new AtomicReference<>(null);
        node.lookupAll(".photo-title").forEach(label -> {
            if (label instanceof Label && title.equals(((Label) label).getText())) {
                found.set(title);
            }
        });
        return found.get() != null;
    }

    static void runOnFxThread(Runnable action) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            action.run();
            latch.countDown();
        });
        latch.await();
    }

    private static class TestableMainView extends MainView {
        private final Supplier<File> chooserResult;

        TestableMainView(Supplier<File> chooserResult) {
            super();
            this.chooserResult = chooserResult;
        }

        @Override
        protected File showDirectoryDialog(Window owner, DirectoryChooser chooser) {
            return chooserResult.get();
        }
    }

    private static class AlbumTestView extends MainView {
        private final Dialog<AlbumSelection> dialog;

        AlbumTestView(PhotoLibraryService service, Dialog<AlbumSelection> dialog) {
            super(service, new PhotoFileScanner(), new ThumbnailService(), new ExportService());
            this.dialog = dialog;
        }

        @Override
        protected Dialog<AlbumSelection> buildAlbumDialog(Window owner, List<PhotoItem> activePhotos) {
            return dialog;
        }
    }

    private static class DetailTestView extends MainView {
        final AtomicBoolean detailShown = new AtomicBoolean(false);
        PhotoItem capturedItem;

        DetailTestView(PhotoLibraryService service) {
            super(service, new PhotoFileScanner(), new ThumbnailService(), new ExportService());
        }

        @Override
        protected Dialog<Void> buildPhotoDetailDialog(PhotoItem item, Window owner) {
            capturedItem = item;
            detailShown.set(true);
            Dialog<Void> dialog = new Dialog<>();
            dialog.setResult(null);
            dialog.setOnShowing(event -> Platform.runLater(dialog::close));
            return dialog;
        }
    }

    private static class FavoriteTestView extends MainView {
        FavoriteTestView(PhotoLibraryService service) {
            super(service, new PhotoFileScanner(), new ThumbnailService(), new ExportService());
        }
    }

    private static class ScanSelectionTestView extends MainView {
        private final Dialog<ScanSelection> dialog;

        ScanSelectionTestView(PhotoLibraryService service, Dialog<ScanSelection> dialog) {
            super(service, new PhotoFileScanner(), new ThumbnailService(), new ExportService());
            this.dialog = dialog;
        }

        @Override
        protected Dialog<ScanSelection> buildScanSelectionDialog(Window owner, List<PhotoItem> items) {
            return dialog;
        }
    }
}
