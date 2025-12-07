package org.example.core;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

public record Photo(
    Path path, String fileName, long sizeBytes, Instant createdAt, Set<Tag> tags, Rating rating) {
  public Photo {
    Objects.requireNonNull(path, "Le chemin de la photo est obligatoire");
    Objects.requireNonNull(fileName, "Le nom de fichier est obligatoire");
    Objects.requireNonNull(createdAt, "La date de creation est obligatoire");
    tags = tags == null ? Set.of() : Set.copyOf(tags);
    rating = rating == null ? Rating.unrated() : rating;
  }
}
