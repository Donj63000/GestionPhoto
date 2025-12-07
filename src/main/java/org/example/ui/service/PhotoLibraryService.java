package org.example.ui.service;

import org.example.ui.model.PhotoItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public class PhotoLibraryService {
    private static final Logger log = LoggerFactory.getLogger(PhotoLibraryService.class);
    private final List<PhotoItem> items;

    public PhotoLibraryService() {
        this.items = new ArrayList<>(seed());
        log.info("PhotoLibraryService initialise avec {} photos de demo", items.size());
    }

    public synchronized List<PhotoItem> all() {
        return items.stream()
                .sorted(byMostRecent())
                .toList();
    }

    public synchronized List<PhotoItem> filter(String search, Filter preset) {
        String normalized = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        return items.stream()
                .filter(item -> switch (preset) {
                    case FAVORITES -> item.favorite();
                    case RECENTS -> item.date().isAfter(LocalDate.now().minusMonths(3));
                    case ALBUMS -> !item.albums().isEmpty();
                    case ALL -> true;
                })
                .filter(item -> normalized.isEmpty()
                        || item.title().toLowerCase(Locale.ROOT).contains(normalized)
                        || item.tags().stream().anyMatch(tag -> tag.toLowerCase(Locale.ROOT).contains(normalized))
                        || item.albums().stream().anyMatch(album -> album.toLowerCase(Locale.ROOT).contains(normalized)))
                .sorted(byMostRecent())
                .collect(Collectors.toList());
    }

    public synchronized void replaceAll(List<PhotoItem> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(enrichAlbums(newItems));
        }
        log.info("Bibliotheque mise a jour: {} elements", items.size());
    }

    public synchronized boolean toggleFavorite(Path path) {
        if (path == null) {
            log.warn("Impossible de basculer le favori: chemin null");
            return false;
        }
        for (int i = 0; i < items.size(); i++) {
            PhotoItem current = items.get(i);
            if (current.path().equals(path)) {
                boolean newStatus = !current.favorite();
                items.set(i, new PhotoItem(current.path(), current.title(), current.date(), current.sizeLabel(),
                        current.tags(), current.albums(), newStatus));
                log.info("Statut favori mis a jour pour {}: {}", path.getFileName(), newStatus);
                return newStatus;
            }
        }
        log.warn("Photo introuvable pour basculer le favori: {}", path);
        return false;
    }

    public synchronized List<PhotoItem> createAlbum(String albumName, List<PhotoItem> photos) {
        if (albumName == null || albumName.isBlank()) {
            log.warn("Creation d'album ignoree: nom vide");
            return List.copyOf(items);
        }
        if (photos == null || photos.isEmpty()) {
            log.warn("Creation d'album ignoree: aucune photo selectionnee");
            return List.copyOf(items);
        }

        String normalized = albumName.trim();
        Set<Path> selectedPaths = photos.stream()
                .map(PhotoItem::path)
                .collect(Collectors.toSet());

        for (int i = 0; i < items.size(); i++) {
            PhotoItem current = items.get(i);
            if (selectedPaths.contains(current.path())) {
                List<String> albums = new ArrayList<>(current.albums());
                boolean alreadyPresent = albums.stream()
                        .anyMatch(existing -> existing.equalsIgnoreCase(normalized));
                if (!alreadyPresent) {
                    albums.add(normalized);
                    items.set(i, new PhotoItem(current.path(), current.title(), current.date(), current.sizeLabel(),
                            current.tags(), albums, current.favorite()));
                }
            }
        }
        log.info("Album '{}' cree avec {} photos", normalized, selectedPaths.size());
        return List.copyOf(items);
    }

    private Comparator<PhotoItem> byMostRecent() {
        return Comparator.comparing(PhotoItem::date).reversed()
                .thenComparing(PhotoItem::title, String.CASE_INSENSITIVE_ORDER);
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
                List.of("vacances", "famille"), List.of("Vacances", "Ete"), true));
        list.add(new PhotoItem(Path.of("demo/anniversaire.jpg"), "Anniversaire", LocalDate.now().minusMonths(1), "3.4 MB",
                List.of("famille", "gateau"), List.of("Famille"), false));
        list.add(new PhotoItem(Path.of("demo/randonnee.jpg"), "Randonnee", LocalDate.now().minusMonths(4), "2.9 MB",
                List.of("montagne", "sport"), List.of(), false));
        list.add(new PhotoItem(Path.of("demo/chat.jpg"), "Chat endormi", LocalDate.now().minusDays(20), "1.2 MB",
                List.of("animal", "maison"), List.of(), true));
        list.add(new PhotoItem(Path.of("demo/concert.jpg"), "Concert", LocalDate.now().minusMonths(2), "4.5 MB",
                List.of("musique", "amis"), List.of("Sorties"), false));
        list.add(new PhotoItem(Path.of("demo/jardin.jpg"), "Jardin fleuri", LocalDate.now().minusDays(15), "2.0 MB",
                List.of("nature", "couleurs"), List.of(), false));
        list.add(new PhotoItem(Path.of("demo/souvenir-ete.jpg"), "Souvenir d'ete", LocalDate.now().minusMonths(6), "3.0 MB",
                List.of("voyage", "soleil"), List.of("Voyages"), false));
        list.add(new PhotoItem(Path.of("demo/noel.jpg"), "Noel", LocalDate.now().minusMonths(11), "5.2 MB",
                List.of("famille", "fete"), List.of("Famille"), true));
        return list;
    }

    private List<PhotoItem> enrichAlbums(List<PhotoItem> source) {
        return source.stream()
                .map(item -> item.albums() != null && !item.albums().isEmpty()
                        ? item
                        : new PhotoItem(item.path(), item.title(), item.date(), item.sizeLabel(), item.tags(),
                        deriveAlbumFromPath(item.path()), item.favorite()))
                .toList();
    }

    private List<String> deriveAlbumFromPath(Path path) {
        if (path == null || path.getParent() == null) {
            return List.of();
        }
        return List.of(path.getParent().getFileName().toString());
    }
}
