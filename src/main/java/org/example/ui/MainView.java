package org.example.ui;

import javafx.animation.PauseTransition;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Popup;
import javafx.stage.Window;
import javafx.util.Duration;
import org.example.infra.PhotoFileScanner;
import org.example.infra.ExportService;
import org.example.infra.ThumbnailService;
import org.example.ui.model.PhotoItem;
import org.example.ui.service.PhotoLibraryService;
import org.example.ui.service.PhotoLibraryService.Filter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class MainView {
    private static final Logger log = LoggerFactory.getLogger(MainView.class);

    private final BorderPane root;
    private final PhotoLibraryService photoService;
    private final PhotoFileScanner scanner;
    private final ThumbnailService thumbnailService;
    private final ExportService exportService;
    private final TilePane grid;
    private final ToggleGroup filterGroup;
    private final TextField searchField;
    private final Label statusLabel;

    public MainView() {
        this(new PhotoLibraryService(), new PhotoFileScanner(), new ThumbnailService(), new ExportService());
    }

    public MainView(PhotoLibraryService photoService, PhotoFileScanner scanner,
            ThumbnailService thumbnailService, ExportService exportService) {
        this.root = new BorderPane();
        this.photoService = photoService;
        this.scanner = scanner;
        this.thumbnailService = thumbnailService;
        this.exportService = exportService;
        this.grid = new TilePane();
        this.filterGroup = new ToggleGroup();
        this.searchField = new TextField();
        this.statusLabel = new Label("Aucune photo importee");
        root.getStyleClass().add("app-root");
        root.setTop(buildHeader());
        root.setLeft(buildSidebar());
        root.setCenter(buildContent());
        refreshGrid();
        log.debug("MainView structure initialisee");
    }

    public BorderPane getRoot() {
        return root;
    }

    private Node buildHeader() {
        VBox container = new VBox();
        container.getStyleClass().add("header");
        container.setPadding(new Insets(16, 24, 16, 24));
        container.setSpacing(8);

        HBox topRow = new HBox(12);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("Photos Gestion");
        title.getStyleClass().add("title");
        Label subtitle = new Label("Galerie simple pour tout le monde");
        subtitle.getStyleClass().add("subtitle");
        VBox titles = new VBox(2, title, subtitle);

        Button helpButton = new Button("Aide");
        helpButton.getStyleClass().add("ghost-button");
        helpButton.setOnAction(event -> showHelpDialog(helpButton.getScene().getWindow()));
        Button scanAllButton = new Button("Scanner tout");
        scanAllButton.getStyleClass().add("secondary-button");
        scanAllButton.setMinHeight(40);
        scanAllButton.setOnAction(event -> runGlobalScan(scanAllButton.getScene().getWindow()));
        Button importButton = new Button("Importer des photos");
        importButton.getStyleClass().add("accent-button");
        importButton.setMinHeight(40);
        importButton.setOnAction(event -> launchImport(importButton.getScene().getWindow()));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        topRow.getChildren().addAll(titles, spacer, helpButton, scanAllButton, importButton);

        searchField.setText("");
        searchField.setPromptText("Rechercher par nom, tag ou date...");
        searchField.getStyleClass().add("search-field");
        searchField.setPrefHeight(40);
        searchField.textProperty().addListener((obs, oldVal, newVal) -> refreshGrid());

        HBox filters = new HBox(10,
                buildFilterChip("Tous", Filter.ALL),
                buildFilterChip("Favoris", Filter.FAVORITES),
                buildFilterChip("Recents", Filter.RECENTS),
                buildFilterChip("Albums", Filter.ALBUMS));
        filters.setAlignment(Pos.CENTER_LEFT);

        HBox searchRow = new HBox(12, searchField, filters);
        HBox.setHgrow(searchField, Priority.ALWAYS);
        searchRow.setAlignment(Pos.CENTER_LEFT);

        container.getChildren().addAll(topRow, searchRow);
        return container;
    }

    private Node buildSidebar() {
        VBox sidebar = new VBox(12);
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPadding(new Insets(20));

        Label navTitle = new Label("Navigation");
        navTitle.getStyleClass().add("section-title");

        Button homeButton = createNavButton("Accueil", () -> selectFilterAndRefresh(Filter.ALL));
        Button photosButton = createNavButton("Photos", () -> selectFilterAndRefresh(Filter.ALL));
        Button albumsButton = createNavButton("Albums", () -> selectFilterAndRefresh(Filter.ALBUMS));
        Button favoritesButton = createNavButton("Favoris", () -> selectFilterAndRefresh(Filter.FAVORITES));
        Button todoButton = createNavButton("A trier", () -> selectFilterAndRefresh(Filter.RECENTS));

        VBox navButtons = new VBox(8, homeButton, photosButton, albumsButton, favoritesButton, todoButton);

        Label quickTitle = new Label("Actions rapides");
        quickTitle.getStyleClass().add("section-title");
        Button quickImport = createSecondaryButton("Importer");
        quickImport.setOnAction(event -> launchImport(quickImport.getScene().getWindow()));

        Button createAlbum = createSecondaryButton("Creer un album");
        createAlbum.setOnAction(event -> handleCreateAlbum(createAlbum.getScene().getWindow()));

        Button exportButton = createSecondaryButton("Exporter");
        exportButton.setOnAction(event -> handleExport(exportButton.getScene().getWindow()));

        VBox quickButtons = new VBox(8, quickImport, createAlbum, exportButton);

        sidebar.getChildren().addAll(navTitle, navButtons, quickTitle, quickButtons);
        return sidebar;
    }

    private Node buildContent() {
        VBox content = new VBox(14);
        content.getStyleClass().add("content-area");
        content.setPadding(new Insets(16));

        content.getChildren().addAll(buildHeroSection(), buildGrid());

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.getStyleClass().add("content-scroll");
        return scrollPane;
    }

    private Node buildHeroSection() {
        HBox hero = new HBox(16);
        hero.getStyleClass().add("hero-card");
        hero.setAlignment(Pos.CENTER_LEFT);
        hero.setPadding(new Insets(16));

        VBox text = new VBox(6);
        Label title = new Label("Bienvenue dans votre galerie");
        title.getStyleClass().add("hero-title");
        Label subtitle = new Label("Importez un dossier pour commencer, ou ouvrez vos favoris.");
        subtitle.getStyleClass().add("hero-subtitle");
        text.getChildren().addAll(title, subtitle);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button primary = new Button("Importer un dossier");
        primary.getStyleClass().add("accent-button");
        primary.setMinHeight(42);
        primary.setOnAction(event -> launchImport(primary.getScene().getWindow()));

        Button secondary = new Button("Ouvrir les favoris");
        secondary.getStyleClass().add("ghost-button");
        secondary.setOnAction(event -> {
            filterGroup.getToggles().stream()
                    .filter(t -> t.getUserData() == Filter.FAVORITES)
                    .findFirst()
                    .ifPresent(t -> t.setSelected(true));
            refreshGrid();
        });

        hero.getChildren().addAll(text, spacer, secondary, primary);

        statusLabel.getStyleClass().add("status-label");
        HBox statusRow = new HBox(statusLabel);
        statusRow.setAlignment(Pos.CENTER_LEFT);
        statusRow.setPadding(new Insets(8, 0, 0, 0));
        return new VBox(8, hero, statusRow);
    }

    private Node buildGrid() {
        VBox wrapper = new VBox(8);
        wrapper.getStyleClass().add("grid-wrapper");
        Label label = new Label("Vos photos en vedette");
        label.getStyleClass().add("section-title");

        grid.setPrefColumns(4);
        grid.setHgap(12);
        grid.setVgap(12);
        grid.getStyleClass().add("gallery-grid");

        wrapper.getChildren().addAll(label, grid);
        return wrapper;
    }

    private Node createPhotoCard(PhotoItem item) {
        VBox card = new VBox(8);
        card.getStyleClass().add("photo-card");
        card.setPadding(new Insets(10));
        card.setOnMouseClicked(event -> {
            if (event.getClickCount() == 1) {
                showPhotoDetails(item, card.getScene() == null ? null : card.getScene().getWindow());
            }
        });

        StackPane thumbWrapper = new StackPane();
        thumbWrapper.getStyleClass().add("photo-thumb");
        thumbWrapper.setMinHeight(140);
        thumbWrapper.setMaxHeight(140);

        ImageView imageView = new ImageView();
        imageView.getStyleClass().add("photo-image");
        imageView.setFitWidth(260);
        imageView.setFitHeight(140);
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);

        thumbWrapper.getChildren().add(imageView);

        if (Files.exists(item.path())) {
            thumbnailService.load(item.path(), 320, imageView::setImage,
                    ex -> log.warn("Miniature indisponible pour {}", item.path().getFileName()));
        }

        Label name = new Label(item.title());
        name.getStyleClass().add("photo-title");
        String meta = item.date().toString() + " | " + item.sizeLabel() + (item.favorite() ? " | *" : "");
        Label info = new Label(meta);
        info.getStyleClass().add("photo-meta");

        Button favoriteButton = new Button(item.favorite() ? "★" : "☆");
        favoriteButton.getStyleClass().add("favorite-toggle");
        favoriteButton.setOnAction(event -> {
            boolean nowFavorite = photoService.toggleFavorite(item.path());
            refreshGrid();
            statusLabel.setText(nowFavorite ? "Ajoute aux favoris" : "Retire des favoris");
            event.consume();
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(8, name, spacer, favoriteButton);
        header.setAlignment(Pos.CENTER_LEFT);

        card.getChildren().addAll(thumbWrapper, header, info);
        return card;
    }

    private void showPhotoDetails(PhotoItem item, Window owner) {
        Dialog<Void> dialog = buildPhotoDetailDialog(item, owner);
        if (dialog != null) {
            dialog.showAndWait();
        }
    }

    protected Dialog<Void> buildPhotoDetailDialog(PhotoItem item, Window owner) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle(item.title());
        dialog.setHeaderText("Informations de la photo");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        if (owner != null) {
            dialog.initOwner(owner);
        }

        ImageView preview = new ImageView();
        preview.setFitWidth(380);
        preview.setPreserveRatio(true);
        preview.setSmooth(true);
        if (Files.exists(item.path())) {
            thumbnailService.load(item.path(), 640, preview::setImage,
                    ex -> log.warn("Apercu indisponible pour {}", item.path().getFileName()));
        }

        Label sizeLabel = new Label("Taille : " + item.sizeLabel());
        sizeLabel.getStyleClass().add("photo-detail-meta");
        Label tagLabel = new Label("Tags : " + (item.tags().isEmpty() ? "Aucun" : String.join(", ", item.tags())));
        tagLabel.getStyleClass().add("photo-detail-meta");
        Label albumLabel = new Label("Albums : " + (item.albums().isEmpty() ? "Aucun" : String.join(", ", item.albums())));
        albumLabel.getStyleClass().add("photo-detail-meta");
        Label pathLabel = new Label("Chemin : " + item.path());
        pathLabel.getStyleClass().add("photo-detail-meta");

        VBox content = new VBox(10, preview, sizeLabel, tagLabel, albumLabel, pathLabel);
        content.setPadding(new Insets(10));
        dialog.getDialogPane().setContent(content);
        return dialog;
    }

    private ToggleButton buildFilterChip(String label, Filter filter) {
        ToggleButton button = new ToggleButton(label);
        button.getStyleClass().add("filter-chip");
        button.setMinHeight(32);
        button.setToggleGroup(filterGroup);
        button.setUserData(filter);
        button.setOnAction(event -> refreshGrid());
        if (filter == Filter.ALL) {
            button.setSelected(true);
        }
        return button;
    }

    private Button createNavButton(String label, Runnable action) {
        Button button = new Button(label);
        button.getStyleClass().add("nav-button");
        button.setAlignment(Pos.CENTER_LEFT);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setOnAction(event -> action.run());
        return button;
    }

    private Button createSecondaryButton(String label) {
        Button button = new Button(label);
        button.getStyleClass().add("secondary-button");
        button.setAlignment(Pos.CENTER_LEFT);
        button.setMaxWidth(Double.MAX_VALUE);
        return button;
    }

    private void selectFilterAndRefresh(Filter filter) {
        filterGroup.getToggles().stream()
                .filter(toggle -> toggle.getUserData() == filter)
                .findFirst()
                .ifPresent(toggle -> toggle.setSelected(true));
        log.info("Navigation vers {}", filter);
        refreshGrid();
    }

    private void refreshGrid() {
        Filter activeFilter = getActiveFilter();
        String search = searchField.getText();
        grid.getChildren().clear();
        List<PhotoItem> filtered = photoService.filter(search, activeFilter);
        if (filtered.isEmpty()) {
            boolean emptyLibrary = photoService.all().isEmpty();
            grid.getChildren().add(buildEmptyState(emptyLibrary));
            statusLabel.setText(emptyLibrary
                    ? "Aucune photo importee. Lancez un scan ou importez un dossier."
                    : "Aucun resultat pour ce filtre.");
            log.info("Grid rafraichie: aucun element (filtre={}, recherche='{}', bibliotheque vide={})",
                    activeFilter, search == null ? "" : search.trim(), emptyLibrary);
            return;
        }
        for (PhotoItem item : filtered) {
            grid.getChildren().add(createPhotoCard(item));
        }
        log.info("Grid rafraichie: {} elements (filtre={}, recherche='{}')",
                grid.getChildren().size(), activeFilter, search == null ? "" : search.trim());
        statusLabel.setText("Affichage: " + grid.getChildren().size() + " photos");
    }

    private Node buildEmptyState(boolean emptyLibrary) {
        VBox emptyBox = new VBox(8);
        emptyBox.setAlignment(Pos.CENTER);
        emptyBox.setPadding(new Insets(24));
        emptyBox.getStyleClass().add("empty-state");

        Label title = new Label(emptyLibrary
                ? "Aucune photo importee pour le moment"
                : "Aucun resultat ne correspond a ce filtre");
        title.getStyleClass().add("section-title");

        Label subtitle = new Label(emptyLibrary
                ? "Cliquez sur 'Importer' ou lancez un scan pour ajouter vos premiers souvenirs."
                : "Ajustez la recherche ou choisissez un autre filtre.");
        subtitle.getStyleClass().add("subtitle");
        subtitle.setWrapText(true);

        emptyBox.getChildren().addAll(title, subtitle);
        return emptyBox;
    }

    private Filter getActiveFilter() {
        return filterGroup.getSelectedToggle() == null
                ? Filter.ALL
                : (Filter) filterGroup.getSelectedToggle().getUserData();
    }

    private void launchImport(Window owner) {
        DirectoryChooser chooser = createDirectoryChooser();
        chooser.setTitle("Choisir un dossier de photos");
        File selectedDir = showDirectoryDialog(owner, chooser);
        if (selectedDir == null) {
            statusLabel.setText("Import annule");
            return;
        }
        runScan(selectedDir.toPath());
    }

    protected DirectoryChooser createDirectoryChooser() {
        return new DirectoryChooser();
    }

    protected File showDirectoryDialog(Window owner, DirectoryChooser chooser) {
        return chooser.showDialog(owner);
    }

    private void handleCreateAlbum(Window owner) {
        log.info("Creation d'album demarree");
        statusLabel.setText("Creation d'album en preparation");
        List<PhotoItem> activePhotos = photoService.filter(searchField.getText(), getActiveFilter());
        if (activePhotos.isEmpty()) {
            statusLabel.setText("Aucune photo disponible pour un album");
            Alert emptyAlert = new Alert(Alert.AlertType.INFORMATION);
            emptyAlert.setTitle("Pas de photos");
            emptyAlert.setHeaderText("Impossible de creer un album");
            emptyAlert.setContentText("Aucune photo ne correspond au filtre actuel.");
            if (owner != null) {
                emptyAlert.initOwner(owner);
            }
            emptyAlert.show();
            return;
        }

        Dialog<AlbumSelection> dialog = buildAlbumDialog(owner, activePhotos);
        Optional<AlbumSelection> result = dialog == null ? Optional.empty() : dialog.showAndWait();
        result.filter(selection -> !selection.name().isBlank() && !selection.photos().isEmpty())
                .ifPresent(selection -> {
                    photoService.createAlbum(selection.name(), selection.photos());
                    refreshGrid();
                    statusLabel.setText("Album '" + selection.name() + "' cree");
                    showToast(owner, "Album '" + selection.name() + "' cree (" + selection.photos().size() + " photos)");
                });
    }

    protected Dialog<AlbumSelection> buildAlbumDialog(Window owner, List<PhotoItem> activePhotos) {
        Dialog<AlbumSelection> dialog = new Dialog<>();
        dialog.setTitle("Creer un album");
        dialog.setHeaderText("Choisissez un nom et les photos a inclure");
        if (owner != null) {
            dialog.initOwner(owner);
        }

        ButtonType createButtonType = new ButtonType("Creer", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(createButtonType, ButtonType.CANCEL);

        TextField nameField = new TextField();
        nameField.setPromptText("Nom de l'album");

        List<CheckBox> checkBoxes = activePhotos.stream()
                .map(photo -> {
                    CheckBox box = new CheckBox(photo.title());
                    box.setSelected(true);
                    box.setUserData(photo);
                    return box;
                })
                .collect(Collectors.toList());

        VBox itemsBox = new VBox(6);
        itemsBox.getChildren().addAll(checkBoxes);

        ScrollPane scrollPane = new ScrollPane(itemsBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefViewportHeight(Math.min(240, 30 * checkBoxes.size() + 10));

        VBox content = new VBox(10,
                new Label("Nom de l'album"),
                nameField,
                new Label("Photos a inclure"),
                scrollPane);
        content.setPadding(new Insets(10));

        dialog.getDialogPane().setContent(content);

        Node createButton = dialog.getDialogPane().lookupButton(createButtonType);
        updateCreateAlbumButtonState(createButton, nameField, checkBoxes);

        nameField.textProperty().addListener((obs, oldVal, newVal) ->
                updateCreateAlbumButtonState(createButton, nameField, checkBoxes));
        checkBoxes.forEach(box -> box.selectedProperty().addListener((obs, oldVal, newVal) ->
                updateCreateAlbumButtonState(createButton, nameField, checkBoxes)));

        dialog.setResultConverter(buttonType -> {
            if (buttonType == createButtonType) {
                List<PhotoItem> selection = checkBoxes.stream()
                        .filter(CheckBox::isSelected)
                        .map(box -> (PhotoItem) box.getUserData())
                        .toList();
                return new AlbumSelection(nameField.getText().trim(), selection);
            }
            return null;
        });
        return dialog;
    }

    private void updateCreateAlbumButtonState(Node createButton, TextField nameField, List<CheckBox> checkBoxes) {
        if (createButton == null) {
            return;
        }
        boolean hasName = nameField.getText() != null && !nameField.getText().trim().isEmpty();
        boolean hasSelection = checkBoxes.stream().anyMatch(CheckBox::isSelected);
        createButton.setDisable(!hasName || !hasSelection);
    }

    private void showToast(Window owner, String message) {
        if (owner == null) {
            log.info(message);
            return;
        }
        Label toast = new Label(message);
        toast.getStyleClass().add("toast");
        toast.setStyle("-fx-background-color: rgba(0,0,0,0.8); -fx-text-fill: white; -fx-padding: 10px; -fx-background-radius: 6px;");

        Popup popup = new Popup();
        popup.setAutoHide(true);
        popup.getContent().add(toast);
        popup.show(owner);

        PauseTransition delay = new PauseTransition(Duration.seconds(2.5));
        delay.setOnFinished(event -> popup.hide());
        delay.play();
    }

    private Comparator<PhotoItem> byMostRecent() {
        return Comparator.comparing(PhotoItem::date).reversed()
                .thenComparing(PhotoItem::title, String.CASE_INSENSITIVE_ORDER);
    }

    static class ScanSelection {
        private final List<PhotoItem> photos;
        private final String album;

        ScanSelection(List<PhotoItem> photos, String album) {
            this.photos = photos == null ? List.of() : List.copyOf(photos);
            this.album = album == null ? "" : album;
        }

        public List<PhotoItem> photos() {
            return photos;
        }

        public String album() {
            return album;
        }
    }

    static class AlbumSelection {
        private final String name;
        private final List<PhotoItem> photos;

        AlbumSelection(String name, List<PhotoItem> photos) {
            this.name = name == null ? "" : name;
            this.photos = photos == null ? List.of() : List.copyOf(photos);
        }

        public String name() {
            return name;
        }

        public List<PhotoItem> photos() {
            return photos;
        }
    }

    private void handleExport(Window owner) {
        log.info("Export demarre");
        List<PhotoItem> selection = photoService.filter(searchField.getText(), getActiveFilter());
        if (selection.isEmpty()) {
            statusLabel.setText("Aucune photo a exporter");
            Alert emptyAlert = new Alert(Alert.AlertType.INFORMATION);
            emptyAlert.setTitle("Pas de photos a exporter");
            emptyAlert.setHeaderText(null);
            emptyAlert.setContentText("Aucune photo n'est disponible avec le filtre actuel.");
            emptyAlert.initOwner(owner);
            emptyAlert.show();
            return;
        }

        DirectoryChooser chooser = createDirectoryChooser();
        chooser.setTitle("Choisir le dossier de destination");
        File destination = showDirectoryDialog(owner, chooser);
        if (destination == null) {
            statusLabel.setText("Export annule");
            return;
        }

        ExportFormat format = promptExportFormat(owner);
        if (format == null) {
            statusLabel.setText("Export annule");
            return;
        }

        statusLabel.setText("Export en cours...");
        startExportTask(selection, destination.toPath(), format, owner);
    }

    protected ExportFormat promptExportFormat(Window owner) {
        Dialog<ExportFormat> dialog = buildExportFormatDialog(owner);
        if (dialog == null) {
            log.warn("Aucun dialogue de format fourni, utilisation de la copie simple par defaut");
            return ExportFormat.SIMPLE_COPY;
        }
        return dialog.showAndWait().orElse(null);
    }

    protected Dialog<ExportFormat> buildExportFormatDialog(Window owner) {
        Dialog<ExportFormat> dialog = new Dialog<>();
        dialog.setTitle("Format d'export");
        dialog.setHeaderText("Choisissez comment copier vos photos");
        dialog.initOwner(owner);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        ChoiceBox<ExportFormat> choiceBox = new ChoiceBox<>();
        choiceBox.getItems().addAll(ExportFormat.values());
        choiceBox.getSelectionModel().select(ExportFormat.SIMPLE_COPY);

        VBox content = new VBox(10,
                new Label("Format"),
                choiceBox,
                new Label("Copie simple : duplique les fichiers dans le dossier cible"));
        content.setPadding(new Insets(10));
        dialog.getDialogPane().setContent(content);

        dialog.setResultConverter(buttonType -> buttonType == ButtonType.OK
                ? choiceBox.getValue()
                : null);
        return dialog;
    }

    private void startExportTask(List<PhotoItem> selection, Path destination, ExportFormat format, Window owner) {
        Task<Integer> task = new Task<>() {
            @Override
            protected Integer call() throws Exception {
                updateMessage("Preparation de l'export...");
                return exportService.exportPhotos(selection, destination, progress -> {
                    updateProgress(progress, 1.0);
                    updateMessage(String.format("%.0f%%", progress * 100));
                });
            }
        };

        Dialog<Void> progressDialog = buildExportProgressDialog(owner, task);
        Button closeButton = progressDialog == null
                ? null
                : (Button) progressDialog.getDialogPane().lookupButton(ButtonType.CLOSE);
        if (closeButton != null) {
            closeButton.setDisable(true);
        }

        task.setOnSucceeded(event -> {
            int copied = task.getValue();
            statusLabel.setText("Export termine : " + copied + " fichiers copies");
            showToast(owner, "Export termine : " + copied + " fichiers");
            if (closeButton != null) {
                closeButton.setDisable(false);
            }
            if (progressDialog != null) {
                progressDialog.close();
            }
            log.info("Export reussi vers {} avec format {}", destination, format);
        });
        task.setOnFailed(event -> {
            Throwable error = task.getException();
            log.error("Export echoue vers {}", destination, error);
            statusLabel.setText("Export echoue");
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Export echoue");
            alert.setHeaderText("Impossible de copier les photos");
            alert.setContentText(formatExportErrorMessage(destination, error));
            alert.initOwner(owner);
            alert.showAndWait();
            if (closeButton != null) {
                closeButton.setDisable(false);
            }
            if (progressDialog != null) {
                progressDialog.close();
            }
        });

        Thread thread = new Thread(task, "export-task");
        thread.setDaemon(true);
        thread.start();
    }

    protected Dialog<Void> buildExportProgressDialog(Window owner, Task<?> task) {
        Dialog<Void> progressDialog = new Dialog<>();
        progressDialog.setTitle("Export en cours");
        progressDialog.setHeaderText("Vos photos sont en cours de copie");
        progressDialog.initOwner(owner);
        progressDialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        ProgressBar progressBar = new ProgressBar();
        progressBar.setMinWidth(280);
        progressBar.progressProperty().bind(task.progressProperty());
        Label progressLabel = new Label("Debut de l'export...");
        progressLabel.textProperty().bind(task.messageProperty());

        VBox content = new VBox(10, progressLabel, progressBar);
        content.setPadding(new Insets(12));
        progressDialog.getDialogPane().setContent(content);
        progressDialog.show();
        return progressDialog;
    }

    private String formatExportErrorMessage(Path destination, Throwable error) {
        String base = "Impossible de copier les photos vers '" + destination + "'. Verifiez l'espace et les droits d'ecriture.";
        if (error != null && error.getMessage() != null && !error.getMessage().isBlank()) {
            return base + " Details : " + error.getMessage();
        }
        return base;
    }

    enum ExportFormat {
        SIMPLE_COPY("Copie simple");

        private final String label;

        ExportFormat(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private void runGlobalScan(Window owner) {
        List<Path> roots = detectScanRoots();
        if (roots.isEmpty()) {
            statusLabel.setText("Aucun dossier detecte pour le scan");
            Alert info = new Alert(Alert.AlertType.INFORMATION);
            info.setTitle("Scan global");
            info.setHeaderText("Aucun dossier a scanner");
            info.setContentText("Ajoutez un dossier Images ou un disque externe puis relancez le scan.");
            if (owner != null) {
                info.initOwner(owner);
            }
            info.show();
            return;
        }

        Task<List<PhotoItem>> task = createGlobalScanTask(roots);
        Dialog<Void> progressDialog = buildScanProgressDialog(owner, task);

        task.setOnSucceeded(event -> {
            List<PhotoItem> items = task.getValue();
            if (!task.isCancelled()) {
                handleScanResults(owner, items);
            }
            if (progressDialog != null) {
                progressDialog.close();
            }
        });
        task.setOnCancelled(event -> {
            statusLabel.setText("Scan annule");
            if (progressDialog != null) {
                progressDialog.close();
            }
            log.info("Scan global annule");
        });
        task.setOnFailed(event -> {
            log.error("Scan global echoue", task.getException());
            statusLabel.setText("Erreur pendant le scan");
            if (progressDialog != null) {
                progressDialog.close();
            }
        });

        Thread thread = new Thread(task, "global-scan-task");
        thread.setDaemon(true);
        thread.start();
    }

    private Task<List<PhotoItem>> createGlobalScanTask(List<Path> candidateRoots) {
        List<Path> roots = candidateRoots.stream()
                .filter(this::isUsableDirectory)
                .distinct()
                .toList();
        return new Task<>() {
            @Override
            protected List<PhotoItem> call() {
                if (roots.isEmpty()) {
                    updateMessage("Aucun dossier valide");
                    return List.of();
                }
                List<PhotoItem> aggregated = new ArrayList<>();
                int total = roots.size();
                int processed = 0;
                int photosCount = 0;
                updateProgress(0, total);
                for (Path root : roots) {
                    if (isCancelled()) {
                        updateMessage("Scan annule");
                        break;
                    }
                    updateMessage("Scan de " + root.getFileName());
                    List<PhotoItem> rootItems = scanner.scan(List.of(root));
                    photosCount += rootItems.size();
                    aggregated.addAll(rootItems);
                    processed++;
                    updateProgress(processed, total);
                    updateMessage(String.format(Locale.ROOT, "%d photos trouvees", photosCount));
                }
                aggregated.sort((a, b) -> b.date().compareTo(a.date()));
                return aggregated;
            }
        };
    }

    protected void handleScanResults(Window owner, List<PhotoItem> items) {
        if (items == null || items.isEmpty()) {
            statusLabel.setText("Aucune image trouvee");
            showToast(owner, "Scan termine : aucune image detectee");
            log.info("Scan global termine sans resultat");
            return;
        }
        long duplicatesDetected = items.stream()
                .filter(item -> photoService.contains(item.path()))
                .count();
        Dialog<ScanSelection> dialog = buildScanSelectionDialog(owner, items);
        Optional<ScanSelection> selection = dialog == null ? Optional.empty() : dialog.showAndWait();
        selection.ifPresent(choice -> {
            PhotoLibraryService.AddResult result = photoService.addPhotos(choice.photos(), choice.album());
            refreshGrid();
            String albumLabel = result.affectedAlbums().isEmpty()
                    ? "aucun album"
                    : String.join(", ", result.affectedAlbums());
            int duplicateReport = (int) Math.max(result.duplicateCount(), duplicatesDetected);
            String message = String.format(Locale.ROOT,
                    "%d photos ajoutees (%s). %d doublon(s) ignore(s).",
                    result.addedCount(), albumLabel, duplicateReport);
            statusLabel.setText(message);
            showToast(owner, message);
            log.info("Scan global termine: {} ajouts, {} doublons", result.addedCount(), duplicateReport);
        });
    }

    protected Dialog<ScanSelection> buildScanSelectionDialog(Window owner, List<PhotoItem> items) {
        Dialog<ScanSelection> dialog = new Dialog<>();
        dialog.setTitle("Selection des photos");
        dialog.setHeaderText("Choisissez les photos a ajouter et l'album cible");
        if (owner != null) {
            dialog.initOwner(owner);
        }

        ButtonType validate = new ButtonType("Ajouter", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(validate, ButtonType.CANCEL);

        TextField filterField = new TextField();
        filterField.setPromptText("Filtrer par nom ou album");
        ChoiceBox<String> sortChoice = new ChoiceBox<>(FXCollections.observableArrayList("Plus recent", "Nom A-Z"));
        sortChoice.getSelectionModel().selectFirst();

        ComboBox<String> albumSelector = new ComboBox<>(FXCollections.observableArrayList(photoService.albumNames()));
        albumSelector.setPromptText("Album existant");
        TextField newAlbumField = new TextField();
        newAlbumField.setPromptText("Ou nommez un nouvel album");

        List<PhotoItem> sorted = new ArrayList<>(items);
        sorted.sort(byMostRecent());

        Map<PhotoItem, CheckBox> checkBoxes = new LinkedHashMap<>();
        for (PhotoItem item : sorted) {
            boolean duplicate = photoService.contains(item.path());
            CheckBox box = new CheckBox(item.title() + " • " + item.date());
            box.setSelected(!duplicate);
            box.setDisable(duplicate);
            box.setUserData(item);
            checkBoxes.put(item, box);
        }

        VBox itemsBox = new VBox(6);
        ScrollPane scrollPane = new ScrollPane(itemsBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefViewportHeight(Math.min(320, 30 * checkBoxes.size() + 20));

        Label duplicateLabel = new Label();

        Runnable refreshList = () -> {
            Comparator<PhotoItem> comparator = sortChoice.getSelectionModel().getSelectedIndex() == 1
                    ? Comparator.comparing(PhotoItem::title, String.CASE_INSENSITIVE_ORDER)
                    : byMostRecent();
            String filter = filterField.getText() == null ? "" : filterField.getText().trim().toLowerCase(Locale.ROOT);
            List<PhotoItem> filtered = sorted.stream()
                    .filter(item -> filter.isEmpty()
                            || item.title().toLowerCase(Locale.ROOT).contains(filter)
                            || item.albums().stream().anyMatch(alb -> alb.toLowerCase(Locale.ROOT).contains(filter)))
                    .sorted(comparator)
                    .toList();
            itemsBox.getChildren().setAll(filtered.stream()
                    .map(checkBoxes::get)
                    .toList());
        };

        filterField.textProperty().addListener((obs, oldVal, newVal) -> refreshList.run());
        sortChoice.getSelectionModel().selectedIndexProperty().addListener((obs, oldVal, newVal) -> refreshList.run());
        refreshList.run();

        Node validateButton = dialog.getDialogPane().lookupButton(validate);
        Runnable updateValidateState = () -> {
            boolean hasSelection = checkBoxes.values().stream()
                    .anyMatch(cb -> cb.isSelected() && !cb.isDisable());
            if (validateButton != null) {
                validateButton.setDisable(!hasSelection);
            }
        };
        checkBoxes.values().forEach(cb -> cb.selectedProperty().addListener((obs, oldVal, newVal) -> updateValidateState.run()));
        updateValidateState.run();

        long duplicates = checkBoxes.values().stream().filter(CheckBox::isDisable).count();
        duplicateLabel.setText(duplicates == 0
                ? "Aucun doublon detecte"
                : duplicates + " doublon(s) detecte(s) : ils seront ignores");

        VBox content = new VBox(10,
                new Label("Filtrer et trier"),
                new HBox(8, filterField, sortChoice),
                new Label("Album cible"),
                new HBox(8, albumSelector, newAlbumField),
                duplicateLabel,
                scrollPane);
        content.setPadding(new Insets(10));
        dialog.getDialogPane().setContent(content);

        dialog.setResultConverter(button -> {
            if (button == validate) {
                List<PhotoItem> selection = checkBoxes.values().stream()
                        .filter(cb -> cb.isSelected() && cb.getUserData() instanceof PhotoItem)
                        .map(cb -> (PhotoItem) cb.getUserData())
                        .toList();
                String album = newAlbumField.getText() != null && !newAlbumField.getText().trim().isEmpty()
                        ? newAlbumField.getText().trim()
                        : albumSelector.getValue() == null ? "" : albumSelector.getValue().trim();
                return new ScanSelection(selection, album);
            }
            return null;
        });
        return dialog;
    }

    private Dialog<Void> buildScanProgressDialog(Window owner, Task<?> task) {
        Dialog<Void> progressDialog = new Dialog<>();
        progressDialog.setTitle("Scan en cours");
        progressDialog.setHeaderText("Recherche de vos photos");
        if (owner != null) {
            progressDialog.initOwner(owner);
        }
        ButtonType cancelButtonType = new ButtonType("Annuler", ButtonData.CANCEL_CLOSE);
        progressDialog.getDialogPane().getButtonTypes().add(cancelButtonType);

        ProgressBar progressBar = new ProgressBar();
        progressBar.setMinWidth(280);
        progressBar.progressProperty().bind(task.progressProperty());
        Label progressLabel = new Label("Preparation du scan...");
        progressLabel.textProperty().bind(task.messageProperty());

        VBox content = new VBox(10, progressLabel, progressBar);
        content.setPadding(new Insets(12));
        progressDialog.getDialogPane().setContent(content);

        Node cancelButton = progressDialog.getDialogPane().lookupButton(cancelButtonType);
        if (cancelButton != null) {
            cancelButton.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> {
                if (!task.isDone()) {
                    task.cancel();
                    statusLabel.setText("Scan annule");
                }
                progressDialog.close();
                event.consume();
            });
        }

        progressDialog.show();
        return progressDialog;
    }

    private List<Path> detectScanRoots() {
        Set<Path> roots = new LinkedHashSet<>();
        Path home = Paths.get(System.getProperty("user.home", ""));
        addIfUsable(roots, home.resolve("Pictures"));
        addIfUsable(roots, home.resolve("Images"));
        addIfUsable(roots, home.resolve("Documents"));
        addIfUsable(roots, home.resolve("Photos"));
        addIfUsable(roots, home);
        exploreExternalMounts(Paths.get("/media"), roots);
        exploreExternalMounts(Paths.get("/mnt"), roots);
        exploreExternalMounts(Paths.get("/Volumes"), roots);
        return roots.stream()
                .filter(this::isUsableDirectory)
                .toList();
    }

    private void exploreExternalMounts(Path parent, Set<Path> collector) {
        if (!isUsableDirectory(parent)) {
            return;
        }
        try (var children = Files.list(parent)) {
            children.filter(this::isUsableDirectory).forEach(collector::add);
        } catch (Exception e) {
            log.debug("Impossible d'explorer {}: {}", parent, e.getMessage());
        }
    }

    private void addIfUsable(Set<Path> collector, Path path) {
        if (isUsableDirectory(path)) {
            collector.add(path);
        }
    }

    private boolean isUsableDirectory(Path path) {
        return path != null && Files.isDirectory(path) && Files.isReadable(path);
    }

    private void runScan(Path root) {
        statusLabel.setText("Scan en cours...");
        Task<List<PhotoItem>> task = new Task<>() {
            @Override
            protected List<PhotoItem> call() {
                return scanner.scan(root);
            }
        };
        task.setOnSucceeded(event -> {
            List<PhotoItem> items = task.getValue();
            photoService.replaceAll(items);
            refreshGrid();
            statusLabel.setText(items.isEmpty()
                    ? "Aucune image trouvee dans le dossier"
                    : "Import reussi: " + items.size() + " photos");
            log.info("Import termine depuis {}", root);
        });
        task.setOnFailed(event -> {
            log.error("Import echoue pour {}", root, task.getException());
            statusLabel.setText("Erreur lors de l'import");
        });
        Thread thread = new Thread(task, "scan-task");
        thread.setDaemon(true);
        thread.start();
    }

    public void showHelpDialog(Window owner) {
        log.info("Ouverture de l'aide");
        Alert dialog = new Alert(Alert.AlertType.INFORMATION);
        dialog.setTitle("Aide et support");
        dialog.setHeaderText("Bienvenue dans Photos Gestion");
        dialog.initOwner(owner);
        dialog.getButtonTypes().setAll(ButtonType.CLOSE);

        Label intro = new Label("Retrouvez ici les ressources pour prendre en main l'application.");
        Hyperlink documentation = new Hyperlink("Documentation utilisateur : https://docs.photosgestion.local");
        documentation.setOnAction(event -> documentation.setVisited(true));
        Label contact = new Label("Support : support@photosgestion.local | 01 23 45 67 89");

        VBox content = new VBox(8, intro, documentation, contact);
        content.setPadding(new Insets(10));
        dialog.getDialogPane().setContent(content);

        dialog.showAndWait();
    }
}
