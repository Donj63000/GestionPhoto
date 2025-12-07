package org.example.infra;

import org.example.ui.model.PhotoItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PhotoFileScanner {
    private static final Logger log = LoggerFactory.getLogger(PhotoFileScanner.class);
    private static final Set<String> IMAGE_EXT = new HashSet<>(List.of("jpg", "jpeg", "png", "gif", "bmp", "webp", "heic"));

    public List<PhotoItem> scan(Path root) {
        if (root == null || !Files.isDirectory(root)) {
            log.warn("Scan ignore: chemin invalide {}", root);
            return List.of();
        }
        try (Stream<Path> walk = Files.walk(root)) {
            List<PhotoItem> items = walk
                    .filter(Files::isRegularFile)
                    .filter(this::isImage)
                    .map(this::toPhotoSafe)
                    .flatMap(Optional::stream)
                    .collect(Collectors.toList());
            log.info("Scan termine: {} fichiers images dans {}", items.size(), root);
            return items;
        } catch (IOException e) {
            log.error("Echec du scan du dossier {}", root, e);
            return List.of();
        }
    }

    private Optional<PhotoItem> toPhotoSafe(Path file) {
        try {
            BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
            long size = attrs.size();
            LocalDate date = Instant.ofEpochMilli(attrs.lastModifiedTime().toMillis())
                    .atZone(ZoneId.systemDefault()).toLocalDate();
            String title = file.getFileName().toString();
            String sizeLabel = humanSize(size);
            return Optional.of(new PhotoItem(file, title, date, sizeLabel, List.of(), false));
        } catch (IOException e) {
            log.warn("Lecture ignoree pour {}: {}", file, e.getMessage());
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

    private String humanSize(long bytes) {
        double size = bytes;
        String unit = "B";
        if (size > 1024) {
            size /= 1024;
            unit = "KB";
        }
        if (size > 1024) {
            size /= 1024;
            unit = "MB";
        }
        return String.format(Locale.ROOT, "%.1f %s", size, unit);
    }
}
