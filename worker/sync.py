#!/usr/bin/env python3
"""Worker de synchronisation Garmin.

Tourne sur le serveur, a heure fixe et sur demande. Pour chaque athlete dont un compte
Garmin est relie, il recupere les seances nouvelles, en tire les tours, les zones de
frequence cardiaque et la meteo, puis les pousse par l'API — qui reste seule a savoir
dedupliquer et normaliser.

Il ne touche jamais la base directement : c'est ce qui garantit qu'une seance arrivee par
lui et une seance arrivee par un import manuel suivent exactement les memes regles.

Usage :
    python -m worker.sync                  # tous les athletes relies
    python -m worker.sync --demandes       # seulement ceux qui ont demande un sync
    python -m worker.sync --boucle         # reste en vie et releve les demandes
    python -m worker.sync --depuis 2026-09-01 --athlete <id>
    python -m worker.sync --dry-run        # n'ecrit rien, affiche ce qui partirait
"""

import argparse
import json
import logging
import os
import sys
import time
import urllib.error
import urllib.request
from datetime import date, datetime, timedelta
from pathlib import Path

LOG = logging.getLogger("worker.garmin")

# Pause entre deux appels a Garmin. Le detail d'une seance en demande quatre : sans pause,
# l'API repond 429 au bout de quelques dizaines de seances.
PAUSE_API = 0.4

# Fenetre reprise lorsqu'un athlete n'a jamais ete synchronise.
JOURS_PREMIER_SYNC = 120

# Intervalle de relecture des demandes en mode boucle.
INTERVALLE_BOUCLE_S = 300

# Intervalle du passage complet, tous athletes relies. Une demande immediate est relevee
# en quelques minutes ; ce passage-ci rattrape les seances de ceux qui n'ont rien demande
# — c'est-a-dire le cas courant, l'athlete qui court et ne touche a rien.
INTERVALLE_COMPLET_S = int(os.environ.get("PREPA_INTERVALLE_COMPLET_S", 1800))

TOKENSTORE = Path(os.environ.get("GARMIN_TOKENSTORE", Path.home() / ".garminconnect"))

TYPES_GARMIN = {
    "running": "RUN", "track_running": "RUN", "obstacle_run": "RUN", "street_running": "RUN",
    "trail_running": "TRAIL", "ultra_run": "TRAIL",
    "treadmill_running": "TREADMILL", "indoor_running": "TREADMILL", "virtual_run": "TREADMILL",
    "cycling": "BIKE", "road_biking": "BIKE", "mountain_biking": "BIKE", "gravel_cycling": "BIKE",
    "indoor_cycling": "BIKE", "virtual_ride": "BIKE",
    "lap_swimming": "SWIM", "open_water_swimming": "SWIM",
    "strength_training": "STRENGTH", "indoor_cardio": "STRENGTH", "yoga": "STRENGTH",
    "hiking": "HIKE", "walking": "HIKE",
}


class ErreurWorker(Exception):
    """Echec sur un athlete, qui ne doit pas emporter les autres."""


# ---------------------------------------------------------------------------
# Client de l'API
# ---------------------------------------------------------------------------

class Api:
    def __init__(self, base, cle):
        self.base = base.rstrip("/")
        self.cle = cle

    def appeler(self, methode, chemin, corps=None):
        url = f"{self.base}/api/v1{chemin}"
        donnees = json.dumps(corps).encode() if corps is not None else None
        requete = urllib.request.Request(url, data=donnees, method=methode)
        requete.add_header("X-Service-Key", self.cle)
        if donnees is not None:
            requete.add_header("Content-Type", "application/json")
        try:
            with urllib.request.urlopen(requete, timeout=120) as reponse:
                contenu = reponse.read()
                return json.loads(contenu) if contenu else None
        except urllib.error.HTTPError as e:
            raise ErreurWorker(f"{methode} {chemin} -> {e.code} {e.read().decode(errors='replace')[:300]}") from e
        except urllib.error.URLError as e:
            raise ErreurWorker(f"API injoignable : {e.reason}") from e


# ---------------------------------------------------------------------------
# Conversions
# ---------------------------------------------------------------------------

def entier(valeur):
    return int(round(float(valeur))) if valeur is not None else None


def allure_depuis_vitesse(vitesse_ms):
    """Vitesse en metres par seconde vers une allure en secondes par kilometre."""
    if not vitesse_ms:
        return None
    secondes = 1000.0 / float(vitesse_ms)
    # Hors de ces bornes, la valeur est aberrante : montre a l'arret ou saut de GPS.
    return int(round(secondes)) if 100 < secondes < 3600 else None


def fahrenheit_vers_celsius(f):
    return round((float(f) - 32) * 5 / 9, 1) if f is not None else None


def mph_vers_kmh(mph):
    return round(float(mph) * 1.609, 1) if mph is not None else None


# ---------------------------------------------------------------------------
# Garmin
# ---------------------------------------------------------------------------

def connecter(cible):
    """Ouvre une session Garmin et verifie a qui elle appartient.

    Le jeton est cache par athlete et vaut environ un an : un passage ordinaire ne
    redemande donc pas le mot de passe.
    """
    try:
        from garminconnect import Garmin
    except ImportError as e:
        raise ErreurWorker(
            "La bibliotheque garminconnect est absente de l'environnement du worker") from e

    dossier = TOKENSTORE / str(cible["athleteId"])
    dossier.mkdir(parents=True, exist_ok=True)

    api = Garmin(email=cible["email"], password=cible["motDePasse"])
    try:
        api.login(tokenstore=str(dossier))
    except Exception as e:
        raise ErreurWorker(f"connexion Garmin refusee : {e}") from e

    verifier_identite(api, cible)
    return api


def verifier_identite(api, cible):
    """Refuse de synchroniser si le compte connecte n'est pas celui attendu.

    Sans ce garde-fou, une erreur d'identifiants ecrirait les seances d'un athlete dans
    l'historique d'un autre — et rien ne le signalerait avant longtemps.
    """
    compte = getattr(api, "display_name", None)
    if not compte:
        LOG.warning("Compte Garmin non identifiable pour %s : verification ignoree", cible["athlete"])
        return
    attendu = cible.get("garminDisplayName")
    if attendu and attendu != compte:
        raise ErreurWorker(
            f"mauvais compte Garmin : {compte} connecte, {attendu} attendu pour {cible['athlete']}")


def recuperer_details(api, activite):
    """Donnees riches absentes du resume : tours, zones, meteo. Best-effort par bloc."""
    identifiant = activite.get("activityId")
    details = {"activityId": identifiant, "resume": activite}
    blocs = {
        "splits": lambda: api.get_activity_splits(identifiant),
        "zonesFC": lambda: api.get_activity_hr_in_timezones(identifiant),
        "meteo": lambda: api.get_activity_weather(identifiant),
        "detail": lambda: api.get_activity(identifiant),
    }
    for nom, appel in blocs.items():
        try:
            details[nom] = appel()
        except Exception as e:
            # Une meteo absente ne doit pas faire perdre les tours.
            details[nom] = {"erreur": str(e)}
        time.sleep(PAUSE_API)
    return details


# ---------------------------------------------------------------------------
# Normalisation vers le format d'ingestion
# ---------------------------------------------------------------------------

# Un tour plus court que cela est un artefact — arret de montre, tour fantome de fin
# d'activite — et non une portion de seance.
TOUR_MIN_SECONDES = 10
TOUR_MIN_METRES = 50


def extraire_tours(brut):
    tours = []
    for lap in (brut.get("splits") or {}).get("lapDTOs") or []:
        duree = lap.get("duration") or 0
        distance = lap.get("distance") or 0
        if duree < TOUR_MIN_SECONDES and distance < TOUR_MIN_METRES:
            continue
        tours.append({
            "index": len(tours) + 1,
            "distanceM": entier(distance),
            "dureeSec": entier(duree) or 0,
            "allureSecKm": allure_depuis_vitesse(lap.get("averageSpeed")),
            "gapSecKm": allure_depuis_vitesse(lap.get("avgGradeAdjustedSpeed")),
            "fcMoy": entier(lap.get("averageHR")),
            "fcMax": entier(lap.get("maxHR")),
            "cadenceMoy": entier(lap.get("averageRunCadence")),
            "puissanceMoy": entier(lap.get("averagePower")),
            "denivelePosM": entier(lap.get("elevationGain")),
            "deniveleNegM": entier(lap.get("elevationLoss")),
            "intensite": lap.get("intensityType") or "UNKNOWN",
        })
    return tours


def extraire_zones_fc(brut):
    zones = []
    for zone in brut.get("zonesFC") or []:
        if isinstance(zone, dict):
            zones.append({
                "zone": zone.get("zoneNumber"),
                "secondes": entier(zone.get("secsInZone") or 0),
                "borneBasse": entier(zone.get("zoneLowBoundary")),
            })
    zones.sort(key=lambda z: z["zone"] or 0)
    return zones


def extraire_meteo(brut):
    """La meteo arrive en unites imperiales quelle que soit la langue du compte."""
    meteo = brut.get("meteo")
    if not isinstance(meteo, dict) or not meteo or "erreur" in meteo:
        return None
    return {
        "temperatureC": fahrenheit_vers_celsius(meteo.get("temp")),
        "ressentiC": fahrenheit_vers_celsius(meteo.get("apparentTemp")),
        "humidite": entier(meteo.get("relativeHumidity")),
        "ventKmh": mph_vers_kmh(meteo.get("windSpeed")),
        "description": (meteo.get("weatherTypeDTO") or {}).get("desc"),
    }


def normaliser(brut):
    """Un dump Garmin vers le format attendu par l'API."""
    resume = brut.get("resume") or {}
    summary = (brut.get("detail") or {}).get("summaryDTO") or {}

    def valeur(*cles):
        for cle in cles:
            for source in (summary, resume):
                if source.get(cle) is not None:
                    return source[cle]
        return None

    depart = str(resume.get("startTimeLocal") or summary.get("startTimeLocal") or "")
    depart = depart.replace(" ", "T")[:19]
    if not depart:
        return None

    type_key = (resume.get("activityType") or {}).get("typeKey")
    return {
        "garminActivityId": brut.get("activityId"),
        "source": "GARMIN_API",
        "type": TYPES_GARMIN.get(type_key, "OTHER"),
        "typeGarmin": type_key,
        "titre": resume.get("activityName") or (brut.get("detail") or {}).get("activityName"),
        "lieu": (brut.get("detail") or {}).get("locationName"),
        "startedAtLocal": depart,
        "dureeSec": entier(valeur("duration")) or 0,
        "dureeMouvementSec": entier(valeur("movingDuration")),
        "distanceM": entier(valeur("distance")),
        "allureMoySecKm": allure_depuis_vitesse(valeur("averageSpeed")),
        "gapMoySecKm": allure_depuis_vitesse(valeur("avgGradeAdjustedSpeed")),
        "fcMoy": entier(valeur("averageHR")),
        "fcMax": entier(valeur("maxHR")),
        "fcMin": entier(summary.get("minHR")),
        "cadenceMoy": entier(valeur("averageRunCadence", "averageRunningCadenceInStepsPerMinute")),
        "denivelePosM": entier(valeur("elevationGain")),
        "deniveleNegM": entier(valeur("elevationLoss")),
        "altitudeMinM": entier(valeur("minElevation")),
        "altitudeMaxM": entier(valeur("maxElevation")),
        "calories": entier(valeur("calories")),
        "teAerobie": valeur("trainingEffect", "aerobicTrainingEffect"),
        "teAnaerobie": valeur("anaerobicTrainingEffect"),
        "teLabel": summary.get("trainingEffectLabel"),
        "chargeEntrainement": valeur("activityTrainingLoad"),
        "vo2max": valeur("vO2MaxValue"),
        "tours": extraire_tours(brut),
        "zonesFc": extraire_zones_fc(brut),
        "meteo": extraire_meteo(brut),
    }


# ---------------------------------------------------------------------------
# Synchronisation
# ---------------------------------------------------------------------------

def fenetre_de_depart(cible, depuis):
    if depuis:
        return depuis
    derniere = cible.get("derniereSync")
    if derniere:
        # On reprend la veille : une seance enregistree tardivement serait sinon manquee.
        return (datetime.fromisoformat(derniere.replace("Z", "+00:00")).date() - timedelta(days=1))
    return date.today() - timedelta(days=JOURS_PREMIER_SYNC)


def synchroniser(api_prepa, cible, depuis=None, dry_run=False):
    """Synchronise un athlete. Renvoie le compte rendu a remonter a l'API."""
    LOG.info("Synchronisation de %s", cible["athlete"])
    garmin = connecter(cible)

    debut = fenetre_de_depart(cible, depuis)
    fin = date.today()
    LOG.info("  fenetre du %s au %s", debut, fin)

    activites = garmin.get_activities_by_date(debut.isoformat(), fin.isoformat())
    if not activites:
        LOG.info("  aucune activite sur la periode")
        return {"statut": "OK", "message": "Aucune activite nouvelle", "activitesRecuperees": 0,
                "garminDisplayName": getattr(garmin, "display_name", None)}

    LOG.info("  %d activites a traiter", len(activites))
    lot = []
    for activite in activites:
        brut = recuperer_details(garmin, activite)
        normalisee = normaliser(brut)
        if normalisee:
            lot.append(normalisee)

    if dry_run:
        LOG.info("  [dry-run] %d activites seraient envoyees", len(lot))
        return {"statut": "OK", "message": f"{len(lot)} activites (dry-run)",
                "activitesRecuperees": 0, "garminDisplayName": getattr(garmin, "display_name", None)}

    resultat = api_prepa.appeler(
        "POST", f"/athletes/{cible['athleteId']}/activities/ingest", lot)
    LOG.info("  %s importees, %s enrichies, %s inchangees",
             resultat["importees"], resultat["misesAJour"], resultat["doublons"])

    return {
        "statut": "OK",
        "message": f"{resultat['importees']} importees, {resultat['misesAJour']} enrichies",
        "activitesRecuperees": resultat["importees"],
        "garminDisplayName": getattr(garmin, "display_name", None),
    }


def passage(api_prepa, seulement_demandes=False, athlete=None, depuis=None, dry_run=False):
    """Un tour complet sur les athletes concernes."""
    cibles = api_prepa.appeler(
        "GET", f"/ingest/targets?seulementDemandes={'true' if seulement_demandes else 'false'}") or []
    if athlete:
        cibles = [c for c in cibles if athlete in (c["athleteId"], c["athlete"])]

    if not cibles:
        LOG.info("Rien a synchroniser")
        return 0

    echecs = 0
    for cible in cibles:
        try:
            resultat = synchroniser(api_prepa, cible, depuis, dry_run)
        except ErreurWorker as e:
            # Un athlete en echec ne doit pas empecher les autres d'etre synchronises.
            LOG.error("  echec pour %s : %s", cible["athlete"], e)
            echecs += 1
            resultat = {"statut": statut_derreur(str(e)), "message": str(e)[:500],
                        "activitesRecuperees": 0, "garminDisplayName": None}
        except Exception as e:
            LOG.exception("  erreur inattendue pour %s", cible["athlete"])
            echecs += 1
            resultat = {"statut": "ERREUR", "message": str(e)[:500],
                        "activitesRecuperees": 0, "garminDisplayName": None}

        if not dry_run:
            api_prepa.appeler("POST", "/ingest/sync-status",
                              {"athleteId": cible["athleteId"], **resultat})
    return echecs


def statut_derreur(message):
    """Distingue les pannes reparables des erreurs d'identite, qui demandent une action."""
    if "mauvais compte" in message:
        return "IDENTITE_KO"
    if "connexion Garmin refusee" in message:
        return "AUTH_ERROR"
    return "ERREUR"


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--api", default=os.environ.get("PREPA_API_URL", "http://localhost:8080"))
    parser.add_argument("--key", default=os.environ.get("PREPA_SERVICE_KEY"))
    parser.add_argument("--athlete", help="identifiant ou nom d'un seul athlete")
    parser.add_argument("--depuis", type=date.fromisoformat, help="date de debut (AAAA-MM-JJ)")
    parser.add_argument("--demandes", action="store_true",
                        help="seulement les athletes ayant demande une synchronisation")
    parser.add_argument("--boucle", action="store_true",
                        help="rester en vie et relever les demandes regulierement")
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()

    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")

    if not args.key:
        print("PREPA_SERVICE_KEY est requis (ou --key)", file=sys.stderr)
        return 2

    api_prepa = Api(args.api, args.key)

    if not args.boucle:
        return 1 if passage(api_prepa, args.demandes, args.athlete, args.depuis, args.dry_run) else 0

    LOG.info("Mode boucle : demandes toutes les %d s, passage complet toutes les %d s",
             INTERVALLE_BOUCLE_S, INTERVALLE_COMPLET_S)

    # Le premier passage est complet : au demarrage, on ne sait pas ce qui a ete manque
    # pendant que le worker etait arrete.
    prochain_complet = 0.0
    while True:
        complet = time.monotonic() >= prochain_complet
        try:
            passage(api_prepa, seulement_demandes=not complet, dry_run=args.dry_run)
        except Exception:
            # La boucle survit a tout : un serveur momentanement absent ne doit pas
            # arreter le worker jusqu'au prochain redemarrage.
            LOG.exception("Passage en echec, on reessaiera")
        if complet:
            # Replanifie meme en cas d'echec : reessayer en boucle serree n'aiderait pas,
            # et Garmin repondrait 429.
            prochain_complet = time.monotonic() + INTERVALLE_COMPLET_S
        time.sleep(INTERVALLE_BOUCLE_S)


if __name__ == "__main__":
    sys.exit(main())
