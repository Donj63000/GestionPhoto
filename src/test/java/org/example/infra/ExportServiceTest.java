package org.example.infra;

import org.example.ui.model.PhotoItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExportServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldCopyPhotosIntoTargetDirectory() throws IOException {
        Path sourceDir = Files.createDirectories(tempDir.resolve("source"));
        Path photo1 = Files.writeString(sourceDir.resolve("photo1.jpg"), "demo1");
        Path photo2 = Files.writeString(sourceDir.resolve("photo2.png"), "demo2");
        Path destination = tempDir.resolve("exported");

        List<PhotoItem> items = List.of(
                new PhotoItem(photo1, "photo1.jpg", LocalDate.now(), "1 KB", List.of(), List.of(), false),
                new PhotoItem(photo2, "photo2.png", LocalDate.now(), "1 KB", List.of(), List.of(), false)
        );

        ExportService service = new ExportService();
        List<Double> progress = new ArrayList<>();

        int copied = service.exportPhotos(items, destination, progress::add);

        assertEquals(2, copied, "All files should be copied");
        assertTrue(Files.exists(destination.resolve("photo1.jpg")), "First photo should be present in export folder");
        assertTrue(Files.exists(destination.resolve("photo2.png")), "Second photo should be present in export folder");
        assertTrue(progress.stream().anyMatch(value -> value >= 1.0 - 0.0001), "Progress should reach 100%");
        assertEquals("demo1", Files.readString(destination.resolve("photo1.jpg")), "Content of first file should match");
        assertEquals("demo2", Files.readString(destination.resolve("photo2.png")), "Content of second file should match");
    }
}
