package org.example.ui.service;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.example.ui.model.PhotoItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PhotoLibraryService {
  private static final Logger log = LoggerFactory.getLogger(PhotoLibraryService.class);
  private static final Comparator<PhotoItem> MOST_RECENT =
      Comparator.comparing(PhotoItem::date)
          .reversed()
          .thenComparing(PhotoItem::title, String.CASE_INSENSITIVE_ORDER);
  private final List<PhotoItem> items;

  public PhotoLibraryService() {
    this.items = new ArrayList<>();
    log.info("PhotoLibraryService initialise sans contenu; en attente d'import ou de scan");
  }

  public synchronized List<PhotoItem> all() {
    return List.copyOf(items);
  }

  public synchronized List<PhotoItem> filter(String search, Filter preset) {
    String normalized = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
    return items.stream()
        .filter(
            item ->
                switch (preset) {
                  case FAVORITES -> item.favorite();
                  case RECENTS -> item.date().isAfter(LocalDate.now().minusMonths(3));
                  case ALBUMS -> !item.albums().isEmpty();
                  case ALL -> true;
                })
        .filter(
            item ->
                normalized.isEmpty()
                    || item.normalizedTitle().contains(normalized)
                    || item.normalizedTags().stream().anyMatch(tag -> tag.contains(normalized))
                    || item.normalizedAlbums().stream()
                        .anyMatch(album -> album.contains(normalized)))
        .toList();
  }

  public synchronized void replaceAll(List<PhotoItem> newItems) {
    items.clear();
    if (newItems != null) {
      items.addAll(enrichAlbums(newItems));
      items.sort(MOST_RECENT);
    }
    log.info("Bibliotheque mise a jour: {} elements", items.size());
  }

  public synchronized AddResult addPhotos(List<PhotoItem> newItems, String albumName) {
    if (newItems == null || newItems.isEmpty()) {
      log.info("Ajout ignore: aucune photo selectionnee");
      return new AddResult(0, 0, Set.of());
    }
    String normalizedAlbum = albumName == null ? "" : albumName.trim();
    Set<Path> existingPaths = items.stream().map(PhotoItem::path).collect(Collectors.toSet());

    int duplicateCount = 0;
    Set<String> affectedAlbums = new HashSet<>();
    if (!normalizedAlbum.isBlank()) {
      affectedAlbums.add(normalizedAlbum);
    }

    for (PhotoItem candidate : newItems) {
      if (existingPaths.contains(candidate.path())) {
        duplicateCount++;
        continue;
      }
      List<String> albums = candidate.albums();
      if (!normalizedAlbum.isBlank()
          && candidate.albums().stream()
              .noneMatch(existing -> existing.equalsIgnoreCase(normalizedAlbum))) {
        albums = new ArrayList<>(albums);
        albums.add(normalizedAlbum);
      }
      PhotoItem enriched =
          new PhotoItem(
              candidate.path(),
              candidate.title(),
              candidate.date(),
              candidate.sizeLabel(),
              candidate.tags(),
              albums,
              candidate.favorite());
      insertSorted(enriched);
      existingPaths.add(candidate.path());
    }

    log.info("Ajout termine: {} doublons ignores, taille finale {}", duplicateCount, items.size());
    return new AddResult(
        newItems.size() - duplicateCount, duplicateCount, Set.copyOf(affectedAlbums));
  }

  public synchronized Set<String> albumNames() {
    return items.stream()
        .flatMap(item -> item.albums().stream())
        .collect(
            Collectors.toCollection(() -> new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER)));
  }

  // Resume d'un album pour l'affichage
  public record AlbumInfo(String name, int photoCount, LocalDate mostRecentDate, PhotoItem cover) {}

  /** Retourne la liste des albums presents, avec le nombre de photos et une couverture. */
  public synchronized List<AlbumInfo> listAlbums(String search) {
    String normalized = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);

    Map<String, List<PhotoItem>> byAlbum =
        new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    for (PhotoItem item : items) {
      for (String album : item.albums()) {
        if (album == null || album.isBlank()) {
          continue;
        }
        byAlbum.computeIfAbsent(album, k -> new ArrayList<>()).add(item);
      }
    }

    return byAlbum.entrySet().stream()
        .map(
            entry -> {
              String albumName = entry.getKey();
              List<PhotoItem> photos = entry.getValue();

              // On recupere la photo la plus recente pour la couverture
              PhotoItem mostRecent =
                  photos.stream()
                      .max(Comparator.comparing(PhotoItem::date))
                      .orElse(null);

              LocalDate mostRecentDate = mostRecent != null ? mostRecent.date() : null;

              int count = photos.size();
              return new AlbumInfo(albumName, count, mostRecentDate, mostRecent);
            })
        .filter(
            info ->
                normalized.isEmpty()
                    || info.name().toLowerCase(Locale.ROOT).contains(normalized))
        .toList();
  }

  public synchronized boolean contains(Path path) {
    return items.stream().anyMatch(item -> item.path().equals(path));
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
        items.set(
            i,
            new PhotoItem(
                current.path(),
                current.title(),
                current.date(),
                current.sizeLabel(),
                current.tags(),
                current.albums(),
                newStatus));
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
    Set<Path> selectedPaths = photos.stream().map(PhotoItem::path).collect(Collectors.toSet());

    for (int i = 0; i < items.size(); i++) {
      PhotoItem current = items.get(i);
      if (selectedPaths.contains(current.path())) {
        List<String> albums = new ArrayList<>(current.albums());
        boolean alreadyPresent =
            albums.stream().anyMatch(existing -> existing.equalsIgnoreCase(normalized));
        if (!alreadyPresent) {
          albums.add(normalized);
          items.set(
              i,
              new PhotoItem(
                  current.path(),
                  current.title(),
                  current.date(),
                  current.sizeLabel(),
                  current.tags(),
                  albums,
                  current.favorite()));
        }
      }
    }
    log.info("Album '{}' cree avec {} photos", normalized, selectedPaths.size());
    return List.copyOf(items);
  }

  public record AddResult(int addedCount, int duplicateCount, Set<String> affectedAlbums) {}

  public enum Filter {
    ALL,
    FAVORITES,
    RECENTS,
    ALBUMS
  }

  private List<PhotoItem> enrichAlbums(List<PhotoItem> source) {
    return source.stream()
        .map(
            item ->
                item.albums() != null && !item.albums().isEmpty()
                    ? item
                    : new PhotoItem(
                        item.path(),
                        item.title(),
                        item.date(),
                        item.sizeLabel(),
                        item.tags(),
                        deriveAlbumFromPath(item.path()),
                        item.favorite()))
        .toList();
  }

  private void insertSorted(PhotoItem item) {
    int index = Collections.binarySearch(items, item, MOST_RECENT);
    if (index < 0) {
      index = -index - 1;
    }
    items.add(index, item);
  }

  private List<String> deriveAlbumFromPath(Path path) {
    if (path == null || path.getParent() == null) {
      return List.of();
    }
    return List.of(path.getParent().getFileName().toString());
  }
}
