package org.example.infra;

import org.example.ui.model.PhotoItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhotoFileScannerTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldScanOnlyImageFiles() throws IOException {
        Path albumDir = Files.createDirectories(tempDir.resolve("AlbumOne"));
        Path photo1 = Files.createFile(albumDir.resolve("photo1.jpg"));
        Path photo2 = Files.createFile(tempDir.resolve("photo2.PNG"));
        Files.createFile(tempDir.resolve("document.txt"));

        PhotoFileScanner scanner = new PhotoFileScanner();
        List<PhotoItem> items = scanner.scan(tempDir);

        assertEquals(2, items.size(), "Only image files should be indexed");
        assertTrue(items.stream().allMatch(item -> item.title().toLowerCase().contains("photo")),
                "Indexed files should match image names");
        assertTrue(items.stream().anyMatch(item -> item.path().equals(photo1)),
                "Path of first image should be preserved");
        assertTrue(items.stream().anyMatch(item -> item.path().equals(photo2)),
                "Path of second image should be preserved");
        assertTrue(items.stream().anyMatch(item -> item.path().equals(photo1)
                        && item.albums().contains("AlbumOne")),
                "First image should keep its parent folder as album");
        assertTrue(items.stream().anyMatch(item -> item.path().equals(photo2)
                        && item.albums().isEmpty()),
                "Image at root should not be assigned to an album");
    }
}
