package app.prepa.domain;

/** Intensite d'un tour, telle que Garmin la restitue pour une seance structuree. */
public enum IntensiteTour {
    WARMUP,
    ACTIVE,
    INTERVAL,
    REST,
    RECOVERY,
    COOLDOWN,
    /** Tour sans intensite declaree : sortie en auto-lap. */
    UNKNOWN
}
