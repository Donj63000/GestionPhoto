package org.example.ui;

import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.scene.control.Button;
import javafx.scene.control.Dialog;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;
import org.example.infra.ExportService;
import org.example.infra.PhotoFileScanner;
import org.example.infra.ThumbnailService;
import org.example.ui.model.PhotoItem;
import org.example.ui.service.PhotoLibraryService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExportIntegrationTest {

    @TempDir
    Path tempDir;

    @BeforeAll
    static void setupToolkit() throws Exception {
        MainViewTest.setupToolkit();
    }

    @Test
    void shouldExportSelectionToChosenDirectory() throws Exception {
        Path source = Files.writeString(tempDir.resolve("photo-source.jpg"), "content");
        Path destination = tempDir.resolve("export-dest");

        PhotoItem item = new PhotoItem(source, "photo-source.jpg", LocalDate.now(), "1 KB", List.of(), List.of(), false);
        PhotoLibraryService service = new PhotoLibraryService();
        service.replaceAll(List.of(item));

        CountDownLatch latch = new CountDownLatch(1);
        ExportService exportService = new ExportService();
        TestableExportView view = new TestableExportView(service, exportService, destination, latch);

        Button exportButton = MainViewTest.findButton(view.getRoot(), "Exporter", ".secondary-button");
        MainViewTest.runOnFxThread(exportButton::fire);

        assertTrue(latch.await(5, TimeUnit.SECONDS), "L'export doit se terminer rapidement");
        assertTrue(Files.exists(destination.resolve("photo-source.jpg")), "La photo doit etre copiee dans le dossier");
        assertEquals("content", Files.readString(destination.resolve("photo-source.jpg")), "Le contenu doit etre conserve");
        assertEquals("Export termine : 1 fichiers copies", MainViewTest.findStatusLabel(view.getRoot()).getText());
    }

    private static class TestableExportView extends MainView {
        private final File destination;
        private final CountDownLatch latch;

        TestableExportView(PhotoLibraryService service, ExportService exportService, Path destination, CountDownLatch latch) {
            super(service, new PhotoFileScanner(), new ThumbnailService(), exportService);
            this.destination = destination.toFile();
            this.latch = latch;
        }

        @Override
        protected File showDirectoryDialog(Window owner, DirectoryChooser chooser) {
            return destination;
        }

        @Override
        protected ExportFormat promptExportFormat(Window owner) {
            return ExportFormat.SIMPLE_COPY;
        }

        @Override
        protected Dialog<Void> buildExportProgressDialog(Window owner, Task<?> task) {
            task.addEventHandler(WorkerStateEvent.WORKER_STATE_SUCCEEDED, event -> latch.countDown());
            task.addEventHandler(WorkerStateEvent.WORKER_STATE_FAILED, event -> latch.countDown());
            return null;
        }
    }
}
