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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.LongConsumer;
import java.util.stream.Stream;

public class PhotoFileScanner {
    private static final Logger log = LoggerFactory.getLogger(PhotoFileScanner.class);
    private static final Set<String> IMAGE_EXT = new HashSet<>(List.of("jpg", "jpeg", "png", "gif", "bmp", "webp", "heic"));

    public List<PhotoItem> scan(Path root) {
        return scan(List.of(root));
    }

    public List<PhotoItem> scan(List<Path> roots) {
        return scan(roots, () -> false, null);
    }

    public List<PhotoItem> scan(Path root, BooleanSupplier cancelSignal, LongConsumer progressCallback) {
        return scan(List.of(root), cancelSignal, progressCallback);
    }

    public List<PhotoItem> scan(List<Path> roots, BooleanSupplier cancelSignal, LongConsumer progressCallback) {
        if (roots == null || roots.isEmpty()) {
            log.warn("Scan ignore: aucune racine fournie");
            return List.of();
        }
        BooleanSupplier shouldCancel = cancelSignal != null ? cancelSignal : () -> false;
        LongConsumer progress = progressCallback != null ? progressCallback : count -> { };
        List<PhotoItem> aggregated = new ArrayList<>();
        long visited = 0;
        for (Path root : roots) {
            if (shouldCancel.getAsBoolean()) {
                break;
            }
            if (root == null || !Files.isDirectory(root)) {
                log.warn("Racine ignoree car invalide: {}", root);
                continue;
            }
            int itemsBefore = aggregated.size();
            try (Stream<Path> walk = Files.walk(root)) {
                Iterator<Path> iterator = walk.iterator();
                while (iterator.hasNext() && !shouldCancel.getAsBoolean()) {
                    Path path = iterator.next();
                    if (!Files.isRegularFile(path)) {
                        continue;
                    }
                    visited++;
                    progress.accept(visited);
                    if (!isImage(path)) {
                        continue;
                    }
                    toPhotoSafe(root, path).ifPresent(aggregated::add);
                }
                if (shouldCancel.getAsBoolean()) {
                    log.info("Scan interrompu a la demande apres {} fichiers parcourus", visited);
                    break;
                }
                int rootAdded = aggregated.size() - itemsBefore;
                log.info("Scan termine: {} fichiers images dans {}", rootAdded, root);
            } catch (IOException e) {
                log.error("Echec du scan du dossier {}", root, e);
            }
        }
        aggregated.sort(Comparator.comparing(PhotoItem::date).reversed());
        return aggregated;
    }

    private Optional<PhotoItem> toPhotoSafe(Path root, Path file) {
        try {
            BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
            long size = attrs.size();
            LocalDate date = Instant.ofEpochMilli(attrs.lastModifiedTime().toMillis())
                    .atZone(ZoneId.systemDefault()).toLocalDate();
            String title = file.getFileName().toString();
            String sizeLabel = humanSize(size);
            List<String> albums = extractAlbums(root, file);
            return Optional.of(new PhotoItem(file, title, date, sizeLabel, List.of(), albums, false));
        } catch (IOException e) {
            log.warn("Lecture ignoree pour {}: {}", file, e.getMessage());
            return Optional.empty();
        }
    }

    private List<String> extractAlbums(Path root, Path file) {
        if (root == null || file == null) {
            return List.of();
        }
        Path relative = root.relativize(file).getParent();
        if (relative == null || relative.getNameCount() == 0) {
            return List.of();
        }
        List<String> albums = new ArrayList<>();
        for (int i = 0; i < relative.getNameCount(); i++) {
            String name = relative.getName(i).toString();
            if (!name.isBlank()) {
                albums.add(name);
            }
        }
        return albums;
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
