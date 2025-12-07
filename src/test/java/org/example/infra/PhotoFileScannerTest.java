package org.example.infra;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import org.example.ui.model.PhotoItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

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
        List<PhotoItem> items = scanner.scan(tempDir).photos();

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

    @Test
    void shouldScanMultipleRootsAndSortByDate() throws IOException {
        Path albumDir = Files.createDirectories(tempDir.resolve("AlbumOne"));
        Path secondDir = Files.createDirectories(tempDir.resolve("AlbumTwo"));
        Path newer = Files.createFile(albumDir.resolve("newer.jpg"));
        Path older = Files.createFile(secondDir.resolve("older.png"));

        Files.setLastModifiedTime(newer, FileTime.from(Instant.now()));
        Files.setLastModifiedTime(older, FileTime.from(Instant.now().minusSeconds(3600)));

        PhotoFileScanner scanner = new PhotoFileScanner();
        List<PhotoItem> items = scanner.scan(List.of(albumDir, secondDir)).photos();

        assertEquals(2, items.size(), "All images across roots should be returned");
        assertEquals(newer, items.get(0).path(), "Most recent image should come first");
        assertEquals(older, items.get(1).path(), "Older image should follow");
    }

    @Test
    void shouldStopWhenCancellationRequested() throws IOException {
        for (int i = 0; i < 5; i++) {
            Files.createFile(tempDir.resolve("image-" + i + ".jpg"));
        }
        AtomicBoolean cancel = new AtomicBoolean(false);
        AtomicLong visited = new AtomicLong(0);
        PhotoFileScanner scanner = new PhotoFileScanner();

        List<PhotoItem> items = scanner.scan(tempDir, cancel::get, count -> {
            visited.set(count);
            if (count >= 2) {
                cancel.set(true);
            }
        }).photos();

        assertTrue(visited.get() < 5, "Scan should stop after cancellation request");
        assertTrue(items.size() < 5, "Not all files should be indexed once cancelled");
    }

    @Test
    void shouldReportProgressForEachVisitedFile() throws IOException {
        Files.createFile(tempDir.resolve("photoA.jpg"));
        Files.createFile(tempDir.resolve("photoB.png"));
        Files.createFile(tempDir.resolve("note.txt"));

        List<Long> calls = new ArrayList<>();
        PhotoFileScanner scanner = new PhotoFileScanner();

        scanner.scan(tempDir, () -> false, calls::add);

        assertEquals(3, calls.size(), "Progress should be reported for every visited file");
        assertEquals(3, calls.get(calls.size() - 1), "Last progress value should match total visited files");
    }

    @Test
    void shouldSkipUnreadableDirectoriesButContinueScan() throws IOException {
        Configuration config = Configuration.unix().toBuilder()
                .setAttributeViews("basic", "posix")
                .build();
        try (FileSystem fs = Jimfs.newFileSystem(config)) {
            Path root = Files.createDirectories(fs.getPath("/data"));
            Path readable = Files.createDirectories(root.resolve("public"));
            Path allowedPhoto = Files.createFile(readable.resolve("ok.jpg"));
            Path blocked = Files.createDirectories(root.resolve("protected"));
            Files.createFile(blocked.resolve("secret.png"));
            Files.setPosixFilePermissions(blocked, PosixFilePermissions.fromString("---------"));

            PhotoFileScanner scanner = new PhotoFileScanner();
            PhotoFileScanner.ScanResult result = scanner.scan(root);

            assertTrue(result.photos().stream().anyMatch(item -> item.path().equals(allowedPhoto)),
                    "Readable images should still be indexed");
            assertTrue(result.skippedDirectories().contains(blocked),
                    "Unreadable directories should be reported as skipped");
        }
    }
}
