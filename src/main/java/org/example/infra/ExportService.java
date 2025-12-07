package org.example.infra;

import org.example.ui.model.PhotoItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public class ExportService {
    private static final Logger log = LoggerFactory.getLogger(ExportService.class);

    public int exportPhotos(List<PhotoItem> photos, Path destination, Consumer<Double> progressUpdater) throws IOException {
        Objects.requireNonNull(destination, "destination");
        if (photos == null || photos.isEmpty()) {
            log.warn("Export ignore: aucune photo a copier");
            return 0;
        }
        Files.createDirectories(destination);

        int total = photos.size();
        int exported = 0;
        for (int i = 0; i < total; i++) {
            PhotoItem item = photos.get(i);
            Path source = item.path();
            if (!Files.exists(source)) {
                log.warn("Fichier source introuvable: {}", source);
                updateProgress(progressUpdater, i + 1, total);
                continue;
            }
            Path target = destination.resolve(source.getFileName().toString());
            try {
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                exported++;
                log.info("Photo copiee vers {}", target);
            } catch (IOException e) {
                log.error("Echec de copie {} vers {}", source, target, e);
                throw new IOException("Impossible de copier '" + source.getFileName() + "' : " + e.getMessage(), e);
            }
            updateProgress(progressUpdater, i + 1, total);
        }
        updateProgress(progressUpdater, total, total);
        log.info("Export termine: {} fichiers copies vers {}", exported, destination);
        return exported;
    }

    private void updateProgress(Consumer<Double> progressUpdater, int current, int total) {
        if (progressUpdater != null && total > 0) {
            progressUpdater.accept(current / (double) total);
        }
    }
}
