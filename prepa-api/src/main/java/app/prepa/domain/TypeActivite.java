package app.prepa.domain;

/** Nature d'une activite realisee, normalisee depuis les libelles Garmin. */
public enum TypeActivite {
    RUN,
    TRAIL,
    TREADMILL,
    BIKE,
    SWIM,
    STRENGTH,
    HIKE,
    OTHER;

    /**
     * Activites comptant dans le volume de course a pied.
     * Le tapis en fait partie : l'oublier faisait disparaitre ces seances des syntheses.
     */
    public boolean estCourseAPied() {
        return this == RUN || this == TRAIL || this == TREADMILL;
    }
}
