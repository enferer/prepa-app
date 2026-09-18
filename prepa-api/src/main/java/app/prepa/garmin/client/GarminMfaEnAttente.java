package app.prepa.garmin.client;

import app.prepa.garmin.StatutSync;

/**
 * Garmin reclame un code de verification, et la session SSO reste ouverte en attendant.
 *
 * <p>Le worker Python resolvait ce cas en se relancant a la main dans un terminal sur le
 * serveur, ou quelqu'un tapait le code. Sans worker, il n'y a plus de terminal : le contexte de
 * la session — cookies et jeton anti-rejeu — voyage donc avec l'exception pour etre mis de cote,
 * et un appel ulterieur reprendra le flux la ou il s'est arrete.
 *
 * <p>Il est de courte duree par nature : quelques minutes, apres quoi Garmin oublie la session.
 */
public class GarminMfaEnAttente extends GarminException {

    private final String contexte;

    public GarminMfaEnAttente(String contexte) {
        super(StatutSync.MFA_REQUISE, "Garmin demande un code de vérification à six chiffres");
        this.contexte = contexte;
    }

    /** Etat de la session SSO, a conserver chiffre jusqu'a la saisie du code. */
    public String contexte() {
        return contexte;
    }
}
