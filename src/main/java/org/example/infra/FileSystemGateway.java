package org.example.infra;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.example.core.Photo;
import org.example.core.Rating;
import org.example.core.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileSystemGateway {
  private static final Logger log = LoggerFactory.getLogger(FileSystemGateway.class);
  private static final Set<String> IMAGE_EXT =
      new HashSet<>(List.of("jpg", "jpeg", "png", "gif", "bmp", "webp", "heic"));

  public List<Photo> listPhotos(Path rootDirectory) {
    List<Photo> found = new ArrayList<>();
    try {
      Files.walkFileTree(
          rootDirectory,
          new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
              if (!attrs.isRegularFile()) {
                return FileVisitResult.CONTINUE;
              }
              if (!isImage(file)) {
                return FileVisitResult.CONTINUE;
              }
              toPhoto(file, attrs).ifPresent(found::add);
              return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
              log.warn("Lecture ignoree pour {}: {}", file, exc.getMessage());
              return FileVisitResult.SKIP_SUBTREE;
            }
          });
    } catch (IOException e) {
      log.error("Echec de parcours du dossier {}", rootDirectory, e);
      throw new IllegalStateException("Impossible de parcourir " + rootDirectory, e);
    }
    return List.copyOf(found);
  }

  private Optional<Photo> toPhoto(Path file, BasicFileAttributes attrs) {
    try {
      Instant createdAt =
          attrs.creationTime() != null
              ? attrs.creationTime().toInstant()
              : attrs.lastModifiedTime().toInstant();
      long size = attrs.size();
      String fileName = file.getFileName().toString();
      return Optional.of(
          new Photo(file, fileName, size, createdAt, Set.<Tag>of(), Rating.unrated()));
    } catch (Exception e) {
      log.warn("Impossible de convertir {} en Photo: {}", file, e.getMessage());
      return Optional.empty();
    }
  }

  private boolean isImage(Path path) {
    String name = path.getFileName().toString();
    int idx = name.lastIndexOf('.');
    if (idx < 0 || idx == name.length() - 1) {
      return false;
    }
    String ext = name.substring(idx + 1).toLowerCase(Locale.ROOT);
    return IMAGE_EXT.contains(ext);
  }
}
