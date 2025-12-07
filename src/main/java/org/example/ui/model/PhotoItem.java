package org.example.ui.model;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public record PhotoItem(Path path,
                        String title,
                        LocalDate date,
                        String sizeLabel,
                        List<String> tags,
                        List<String> albums,
                        boolean favorite) {
    public PhotoItem {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(sizeLabel, "sizeLabel");
        tags = tags == null ? List.of() : List.copyOf(tags);
        albums = albums == null ? List.of() : List.copyOf(albums);
    }
}
