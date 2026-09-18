package app.prepa.domain;

/**
 * Typologie des seances, reprise de la methodologie du coach.
 *
 * <p>{@code COURSE} est la course objectif elle-meme ; {@code CROSS} couvre le
 * cross-training prescrit en cas de douleur (velo, natation, elliptique).
 */
public enum TypeSeance {
    /** Endurance fondamentale — le socle des 80 %. */
    EF,
    /** Sortie longue. */
    SL,
    /** Seuil / allure semi a 10 km. */
    SEUIL,
    /** Intervalles courts a VMA. */
    VMA,
    /** Allure marathon. */
    AM,
    /** Cotes. */
    COTES,
    /** Renforcement musculaire. */
    RENFO,
    /** La course objectif. */
    COURSE,
    /** Cross-training. */
    CROSS,
    /** Repos prescrit. */
    REPOS;

    /**
     * Seances dont l'allure moyenne a un sens. Sur une seance a intervalles, la moyenne
     * melange effort et recuperation : elle n'est pas comparable a une allure cible.
     */
    public boolean estAllureContinue() {
        return this == EF || this == SL || this == AM || this == COURSE;
    }

    /** Seances de qualite, celles qu'on ne sacrifie pas a la legere. */
    public boolean estQualite() {
        return this == SEUIL || this == VMA || this == AM || this == COTES;
    }

    /** Seances cles au sens des regles d'adaptation : leur absence declenche une question. */
    public boolean estCle() {
        return estQualite() || this == SL;
    }

    /**
     * L'activite qui accomplit cette seance est-elle de cette nature ?
     *
     * <p>Une seance se constate a la nature de ce qui a ete fait, pas au fait qu'on y ait
     * couru : un renfo s'accomplit par une muscu, du cross-training par du velo ou de la
     * natation. Confondre « ne compte pas dans le volume » et « ne se rapproche jamais »
     * laissait le renfo eternellement {@code A_VENIR}, a cocher a la main, alors que la montre
     * l'avait deja dit.
     */
    public boolean estAccompliePar(TypeActivite activite) {
        return switch (this) {
            case RENFO -> activite == TypeActivite.STRENGTH;
            case CROSS -> activite == TypeActivite.BIKE
                    || activite == TypeActivite.SWIM
                    || activite == TypeActivite.OTHER;
            case REPOS -> false;
            default -> activite.estCourseAPied();
        };
    }

    /**
     * Seances dont l'absence se constate. Le repos n'a rien a constater : il n'attend aucune
     * activite, et le declarer « non realise » n'aurait pas de sens.
     */
    public boolean seConstate() {
        return this != REPOS;
    }
}
