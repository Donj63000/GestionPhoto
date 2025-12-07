package org.example.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.example.infra.FileSystemGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ScanService {
  private static final Logger log = LoggerFactory.getLogger(ScanService.class);
  private final FileSystemGateway fileSystemGateway;

  public ScanService(FileSystemGateway fileSystemGateway) {
    this.fileSystemGateway =
        Objects.requireNonNull(fileSystemGateway, "FileSystemGateway manquant");
  }

  public List<Photo> scan(Path rootDirectory) {
    if (rootDirectory == null) {
      log.warn("Scan ignore: chemin nul");
      return List.of();
    }
    if (!Files.isDirectory(rootDirectory)) {
      log.warn("Scan ignore: {} n'est pas un repertoire", rootDirectory);
      return List.of();
    }

    log.info("Demarrage du scan du repertoire {}", rootDirectory);
    try {
      List<Photo> photos = fileSystemGateway.listPhotos(rootDirectory);
      log.info("Scan termine: {} photos trouvees dans {}", photos.size(), rootDirectory);
      return photos;
    } catch (RuntimeException e) {
      log.error("Echec du scan du repertoire {}", rootDirectory, e);
      throw e;
    }
  }
}
