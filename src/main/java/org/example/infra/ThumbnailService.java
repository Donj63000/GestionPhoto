package org.example.infra;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.image.Image;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

public class ThumbnailService {
    private static final Logger log = LoggerFactory.getLogger(ThumbnailService.class);
    private final Map<Path, Image> cache;
    private final int maxEntries;
    private final ExecutorService executor;

    public ThumbnailService() {
        this(128);
    }

    public ThumbnailService(int maxEntries) {
        this.maxEntries = maxEntries;
        this.cache = Collections.synchronizedMap(new LinkedHashMap<Path, Image>(maxEntries, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Path, Image> eldest) {
                return size() > ThumbnailService.this.maxEntries;
            }
        });
        this.executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    }

    public void load(Path path, int targetSize, Consumer<Image> onSuccess, Consumer<Throwable> onError) {
        if (path == null || onSuccess == null || !Files.exists(path)) {
            return;
        }
        Image cached = cache.get(path);
        if (cached != null) {
            onSuccess.accept(cached);
            return;
        }

        Task<Image> task = new Task<>() {
            @Override
            protected Image call() {
                return new Image(path.toUri().toString(), targetSize, targetSize, true, true, true);
            }
        };

        task.setOnSucceeded(event -> {
            Image image = task.getValue();
            cache.put(path, image);
            Platform.runLater(() -> onSuccess.accept(image));
        });

        task.setOnFailed(event -> {
            Throwable ex = task.getException();
            log.warn("Echec de chargement de miniature pour {}", path, ex);
            if (onError != null) {
                Platform.runLater(() -> onError.accept(ex));
            }
        });

        try {
            executor.submit(task);
        } catch (RejectedExecutionException ex) {
            log.warn("Execution refusee pour le chargement de miniature {}", path, ex);
            if (onError != null) {
                Platform.runLater(() -> onError.accept(ex));
            }
        }
    }

    public void shutdown() {
        executor.shutdown();
    }
}
