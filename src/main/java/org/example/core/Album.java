package org.example.core;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record Album(String name, Instant createdAt, List<Photo> photos) {
    public Album {
        Objects.requireNonNull(name, "Le nom de l'album est obligatoire");
        createdAt = createdAt == null ? Instant.now() : createdAt;
        photos = photos == null ? List.of() : List.copyOf(photos);
    }
}
