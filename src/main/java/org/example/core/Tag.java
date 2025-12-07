package org.example.core;

import java.util.Objects;

public record Tag(String label) {
  public Tag {
    Objects.requireNonNull(label, "Le libelle du tag est obligatoire");
  }
}
