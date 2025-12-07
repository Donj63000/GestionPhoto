package org.example.ui.service;

import org.example.ui.model.PhotoItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class PhotoLibraryService {
    private static final Logger log = LoggerFactory.getLogger(PhotoLibraryService.class);
    private final List<PhotoItem> items;

    public PhotoLibraryService() {
        this.items = new ArrayList<>(seed());
        log.info("PhotoLibraryService initialise avec {} photos de demo", items.size());
    }

    public synchronized List<PhotoItem> all() {
        return List.copyOf(items);
    }

    public synchronized List<PhotoItem> filter(String search, Filter preset) {
        String normalized = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        return items.stream()
                .filter(item -> switch (preset) {
                    case FAVORITES -> item.favorite();
                    case RECENTS -> item.date().isAfter(LocalDate.now().minusMonths(3));
                    case ALBUMS, ALL -> true; // albums non implémente pour l'instant
                })
                .filter(item -> normalized.isEmpty()
                        || item.title().toLowerCase(Locale.ROOT).contains(normalized)
                        || item.tags().stream().anyMatch(tag -> tag.toLowerCase(Locale.ROOT).contains(normalized)))
                .collect(Collectors.toList());
    }

    public synchronized void replaceAll(List<PhotoItem> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        log.info("Bibliotheque mise a jour: {} elements", items.size());
    }

    public enum Filter {
        ALL,
        FAVORITES,
        RECENTS,
        ALBUMS
    }

    private List<PhotoItem> seed() {
        List<PhotoItem> list = new ArrayList<>();
        list.add(new PhotoItem(Path.of("demo/plage.jpg"), "Plage estivale", LocalDate.now().minusDays(5), "2.1 MB",
                List.of("vacances", "famille"), true));
        list.add(new PhotoItem(Path.of("demo/anniversaire.jpg"), "Anniversaire", LocalDate.now().minusMonths(1), "3.4 MB",
                List.of("famille", "gateau"), false));
        list.add(new PhotoItem(Path.of("demo/randonnee.jpg"), "Randonnee", LocalDate.now().minusMonths(4), "2.9 MB",
                List.of("montagne", "sport"), false));
        list.add(new PhotoItem(Path.of("demo/chat.jpg"), "Chat endormi", LocalDate.now().minusDays(20), "1.2 MB",
                List.of("animal", "maison"), true));
        list.add(new PhotoItem(Path.of("demo/concert.jpg"), "Concert", LocalDate.now().minusMonths(2), "4.5 MB",
                List.of("musique", "amis"), false));
        list.add(new PhotoItem(Path.of("demo/jardin.jpg"), "Jardin fleuri", LocalDate.now().minusDays(15), "2.0 MB",
                List.of("nature", "couleurs"), false));
        list.add(new PhotoItem(Path.of("demo/souvenir-ete.jpg"), "Souvenir d'ete", LocalDate.now().minusMonths(6), "3.0 MB",
                List.of("voyage", "soleil"), false));
        list.add(new PhotoItem(Path.of("demo/noel.jpg"), "Noel", LocalDate.now().minusMonths(11), "5.2 MB",
                List.of("famille", "fete"), true));
        return list;
    }
}
