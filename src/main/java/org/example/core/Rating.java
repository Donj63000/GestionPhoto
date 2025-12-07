package org.example.core;

public record Rating(int score, String note) {
    public Rating {
        if (score < 0 || score > 5) {
            throw new IllegalArgumentException("La note doit etre comprise entre 0 et 5");
        }
        note = note == null ? "" : note;
    }

    public static Rating unrated() {
        return new Rating(0, "");
    }
}
