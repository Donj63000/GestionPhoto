package org.example.infra;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.LongConsumer;
import org.example.ui.model.PhotoItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PhotoFileScanner {
  private static final Logger log = LoggerFactory.getLogger(PhotoFileScanner.class);
  private static final Set<String> IMAGE_EXT =
      new HashSet<>(List.of("jpg", "jpeg", "png", "gif", "bmp", "webp", "heic"));
  private static final Set<String> SYSTEM_DIR_NAMES =
      Set.of(
          "windows",
          "program files",
          "program files (x86)",
          "appdata",
          "$recycle.bin",
          "system volume information");

  public record ScanOptions(
      boolean skipHidden, boolean skipSystem, int maxDepth, long maxSizeBytes, LocalDate minDate) {
    public static ScanOptions defaults() {
      return new ScanOptions(true, true, Integer.MAX_VALUE, 0, null);
    }
  }

  public ScanResult scan(Path root) {
    return scan(List.of(root));
  }

  public ScanResult scan(List<Path> roots) {
    return scan(roots, () -> false, null, ScanOptions.defaults());
  }

  public ScanResult scan(Path root, BooleanSupplier cancelSignal, LongConsumer progressCallback) {
    return scan(List.of(root), cancelSignal, progressCallback, ScanOptions.defaults());
  }

  public ScanResult scan(
      Path root, BooleanSupplier cancelSignal, LongConsumer progressCallback, ScanOptions options) {
    return scan(List.of(root), cancelSignal, progressCallback, options);
  }

  public ScanResult scan(
      List<Path> roots, BooleanSupplier cancelSignal, LongConsumer progressCallback) {
    return scan(roots, cancelSignal, progressCallback, ScanOptions.defaults());
  }

  public ScanResult scan(
      List<Path> roots,
      BooleanSupplier cancelSignal,
      LongConsumer progressCallback,
      ScanOptions options) {
    if (roots == null || roots.isEmpty()) {
      log.warn("Scan ignore: aucune racine fournie");
      return ScanResult.empty();
    }
    BooleanSupplier shouldCancel = cancelSignal != null ? cancelSignal : () -> false;
    LongConsumer progress = progressCallback != null ? progressCallback : count -> {};
    ScanOptions effectiveOptions = options == null ? ScanOptions.defaults() : options;
    List<PhotoItem> aggregated = new ArrayList<>();
    List<Path> skippedDirectories = new ArrayList<>();
    AtomicLong visited = new AtomicLong(0);
    for (Path root : roots) {
      if (shouldCancel.getAsBoolean()) {
        break;
      }
      if (root == null || !Files.isDirectory(root)) {
        log.warn("Racine ignoree car invalide: {}", root);
        continue;
      }
      int itemsBefore = aggregated.size();
      try {
        Files.walkFileTree(
            root,
            EnumSet.noneOf(FileVisitOption.class),
            Integer.MAX_VALUE,
            new ControlledVisitor(
                root,
                shouldCancel,
                progress,
                visited,
                aggregated,
                skippedDirectories,
                effectiveOptions));
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
    return new ScanResult(aggregated, List.copyOf(skippedDirectories));
  }

  private Optional<PhotoItem> toPhotoSafe(Path root, Path file, BasicFileAttributes attrs) {
    long size = attrs.size();
    LocalDate date =
        Instant.ofEpochMilli(attrs.lastModifiedTime().toMillis())
            .atZone(ZoneId.systemDefault())
            .toLocalDate();
    String title = file.getFileName().toString();
    String sizeLabel = humanSize(size);
    List<String> albums = extractAlbums(root, file);
    return Optional.of(new PhotoItem(file, title, date, sizeLabel, List.of(), albums, false));
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

  private boolean isReadableDirectory(Path dir) {
    if (!Files.isReadable(dir)) {
      return false;
    }
    try {
      PosixFileAttributeView view = Files.getFileAttributeView(dir, PosixFileAttributeView.class);
      if (view != null) {
        Set<PosixFilePermission> permissions = view.readAttributes().permissions();
        return permissions.contains(PosixFilePermission.OWNER_READ)
            || permissions.contains(PosixFilePermission.GROUP_READ)
            || permissions.contains(PosixFilePermission.OTHERS_READ);
      }
    } catch (IOException e) {
      log.warn("Impossible de verifier les droits pour {}: {}", dir, e.getMessage());
      return false;
    }
    return true;
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

  public record ScanResult(List<PhotoItem> photos, List<Path> skippedDirectories) {
    public static ScanResult empty() {
      return new ScanResult(List.of(), List.of());
    }
  }

  private class ControlledVisitor extends SimpleFileVisitor<Path> {
    private final Path root;
    private final BooleanSupplier shouldCancel;
    private final LongConsumer progress;
    private final AtomicLong visited;
    private final List<PhotoItem> aggregated;
    private final List<Path> skipped;
    private final ScanOptions options;

    ControlledVisitor(
        Path root,
        BooleanSupplier shouldCancel,
        LongConsumer progress,
        AtomicLong visited,
        List<PhotoItem> aggregated,
        List<Path> skipped,
        ScanOptions options) {
      this.root = root;
      this.shouldCancel = shouldCancel;
      this.progress = progress;
      this.visited = visited;
      this.aggregated = aggregated;
      this.skipped = skipped;
      this.options = options == null ? ScanOptions.defaults() : options;
    }

    @Override
    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
      if (shouldCancel.getAsBoolean()) {
        return FileVisitResult.TERMINATE;
      }
      if (options.skipHidden()) {
        if (isHidden(dir) || dir.getFileName().toString().startsWith(".")) {
          skipped.add(dir);
          return FileVisitResult.SKIP_SUBTREE;
        }
      }
      if (options.skipSystem()) {
        String name = dir.getFileName().toString().toLowerCase(Locale.ROOT);
        if (SYSTEM_DIR_NAMES.contains(name)) {
          skipped.add(dir);
          return FileVisitResult.SKIP_SUBTREE;
        }
      }
      if (options.maxDepth() > 0) {
        int depth = root.relativize(dir).getNameCount();
        if (depth >= options.maxDepth()) {
          return FileVisitResult.SKIP_SUBTREE;
        }
      }
      if (!isReadableDirectory(dir)) {
        skipped.add(dir);
        log.warn("Repertoire ignore (acces refuse): {}", dir);
        return FileVisitResult.SKIP_SUBTREE;
      }
      return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
      if (shouldCancel.getAsBoolean()) {
        return FileVisitResult.TERMINATE;
      }
      if (!attrs.isRegularFile()) {
        return FileVisitResult.CONTINUE;
      }
      if (options.maxDepth() > 0) {
        int depth = root.relativize(file).getNameCount();
        if (depth > options.maxDepth()) {
          return FileVisitResult.CONTINUE;
        }
      }
      long count = visited.incrementAndGet();
      progress.accept(count);
      if (!isImage(file)) {
        return FileVisitResult.CONTINUE;
      }
      if (options.maxSizeBytes() > 0 && attrs.size() > options.maxSizeBytes()) {
        return FileVisitResult.CONTINUE;
      }
      if (options.minDate() != null) {
        LocalDate fileDate =
            Instant.ofEpochMilli(attrs.lastModifiedTime().toMillis())
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
        if (fileDate.isBefore(options.minDate())) {
          return FileVisitResult.CONTINUE;
        }
      }
      toPhotoSafe(root, file, attrs).ifPresent(aggregated::add);
      return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFileFailed(Path file, IOException exc) {
      skipped.add(file);
      log.warn("Lecture ignoree pour {}: {}", file, exc.getMessage());
      return FileVisitResult.SKIP_SUBTREE;
    }
  }

  private boolean isHidden(Path dir) {
    try {
      return Files.isHidden(dir);
    } catch (IOException e) {
      return false;
    }
  }
}
