package org.example.infra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.example.ui.model.PhotoItem;
import org.junit.jupiter.api.Test;

class ExportServiceTest {

  @Test
  void shouldGenerateUniqueNameWhenFilesShareSameName() throws IOException {
    FileSystem fs = Jimfs.newFileSystem(Configuration.unix());
    Path sourceRoot = fs.getPath("/photos");
    Files.createDirectories(sourceRoot.resolve("album"));

    Path firstSource = Files.writeString(sourceRoot.resolve("photo.jpg"), "original");
    Path secondSource = Files.writeString(sourceRoot.resolve("album/photo.jpg"), "duplicate");

    PhotoItem firstItem =
        new PhotoItem(
            firstSource, "Photo", LocalDate.of(2024, 1, 10), "1KB", List.of(), List.of(), false);
    PhotoItem secondItem =
        new PhotoItem(
            secondSource, "Photo", LocalDate.of(2024, 1, 11), "1KB", List.of(), List.of(), false);

    Path destination = fs.getPath("/export");
    ExportService service = new ExportService();

    int exported = service.exportPhotos(List.of(firstItem, secondItem), destination, null);

    Path expectedFirst = destination.resolve("photo.jpg");
    Path expectedSecond = destination.resolve("photo (1).jpg");

    assertEquals(2, exported);
    assertTrue(Files.exists(expectedFirst), "Le premier fichier doit exister");
    assertTrue(Files.exists(expectedSecond), "Le second fichier doit etre renomme et copie");
    assertEquals("original", Files.readString(expectedFirst));
    assertEquals("duplicate", Files.readString(expectedSecond));
  }
}
