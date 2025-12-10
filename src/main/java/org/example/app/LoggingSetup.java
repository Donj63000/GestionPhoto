package org.example.app;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.Level;
import org.slf4j.LoggerFactory;

public final class LoggingSetup {
  private static final org.slf4j.Logger log = LoggerFactory.getLogger(LoggingSetup.class);

  private LoggingSetup() {}

  public static void prepare() {
    Path logDir = Path.of(System.getProperty("app.log.dir", "logs"));
    try {
      Files.createDirectories(logDir);
      log.info("Dossier de logs disponible : {}", logDir.toAbsolutePath());
    } catch (IOException e) {
      System.err.println(
          "Impossible de preparer le dossier de logs '" + logDir + "' : " + e.getMessage());
    }
    if (log instanceof Logger logger) {
      Level level = logger.getEffectiveLevel();
      log.info("Niveau de log actif : {}", level);
    }
  }
}
