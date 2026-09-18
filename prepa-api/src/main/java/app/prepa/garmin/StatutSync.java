package app.prepa.garmin;

/**
 * Issue d'un passage de synchronisation pour un athlete.
 *
 * <p>Les trois derniers ne sont pas de simples nuances d'echec : ils appellent des gestes
 * differents. Une {@link #ERREUR} se reessaie toute seule au passage suivant ; un
 * {@link #AUTH_ERROR} demande de ressaisir les identifiants ; un {@link #IDENTITE_KO} veut
 * dire qu'on a failli ecrire les seances d'un athlete chez un autre et qu'il ne faut surtout
 * pas reessayer ; une {@link #MFA_REQUISE} attend un code a six chiffres. Les distinguer,
 * c'est ce qui permet a l'ecran d'exploitation de dire quoi faire plutot que « ca a rate ».
 */
public enum StatutSync {

    /** Le passage tourne. Pose a l'entree, remplace dans tous les cas a la sortie. */
    EN_COURS,

    OK,

    /** Garmin a refuse les identifiants. */
    AUTH_ERROR,

    /** Le compte connecte n'est pas celui memorise sur l'athlete : le garde-fou a coupe. */
    IDENTITE_KO,

    /** Garmin reclame une verification en deux etapes ; le code reste a fournir. */
    MFA_REQUISE,

    /** Tout le reste : panne reseau, reponse inattendue, 429. */
    ERREUR;

    public boolean estUnEchec() {
        return this != OK && this != EN_COURS;
    }
}
