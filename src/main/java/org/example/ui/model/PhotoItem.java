package org.example.ui.model;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Locale;

public record PhotoItem(Path path,
                        String title,
                        String normalizedTitle,
                        LocalDate date,
                        String sizeLabel,
                        List<String> tags,
                        List<String> normalizedTags,
                        List<String> albums,
                        List<String> normalizedAlbums,
                        boolean favorite) {

    public PhotoItem(Path path,
                     String title,
                     LocalDate date,
                     String sizeLabel,
                     List<String> tags,
                     List<String> albums,
                     boolean favorite) {
        this(path,
                title,
                normalize(title),
                date,
                sizeLabel,
                safeList(tags),
                normalizeList(tags),
                safeList(albums),
                normalizeList(albums),
                favorite);
    }

    public PhotoItem {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(normalizedTitle, "normalizedTitle");
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(sizeLabel, "sizeLabel");
        tags = safeList(tags);
        normalizedTags = safeList(normalizedTags);
        albums = safeList(albums);
        normalizedAlbums = safeList(normalizedAlbums);
    }

    private static List<String> safeList(List<String> source) {
        return source == null ? List.of() : List.copyOf(source);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private static List<String> normalizeList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(Objects::nonNull)
                .map(PhotoItem::normalize)
                .toList();
    }
}
