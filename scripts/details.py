#!/usr/bin/env python3
"""details.py — condense les JSON bruts Garmin en fiches de séance exploitables.

Les fichiers `data/garmin_raw/<activityId>.json` pèsent ~40 Ko chacun et
contiennent des centaines de champs (GPS point par point, métadonnées device…).
Les charger tels quels noierait aussi bien la vue que l'analyse du coach.

Ce module en extrait une **fiche compacte** (~1-2 Ko) : tours, zones FC, météo,
training effect. C'est cette fiche — et jamais le brut — qui alimente
`vue/data.js`, `data/detail_seances.json` et les analyses.

stdlib uniquement. Utilisé par build_data.py et analyze.py.
"""

import json
from pathlib import Path

# Un tour plus court que ça est un artefact (arrêt de montre, tour fantôme de
# fin d'activité), pas une portion de séance : on l'écarte de la fiche.
TOUR_MIN_SECONDES = 10
TOUR_MIN_METRES = 50

# L'API Garmin sert la météo en unités impériales quelle que soit la langue
# du compte : °F et mph.
def _f_vers_c(f):
    return round((float(f) - 32) * 5 / 9, 1) if f is not None else None


def _mph_vers_kmh(mph):
    return round(float(mph) * 1.609, 1) if mph is not None else None


def _allure(vitesse_ms):
    """m/s -> secondes par km (None si vitesse nulle ou aberrante)."""
    if not vitesse_ms:
        return None
    sec = 1000.0 / float(vitesse_ms)
    return int(round(sec)) if 100 < sec < 3600 else None


def _arrondi(val, dec=2):
    return round(float(val), dec) if val is not None else None


def _entier(val):
    return int(round(float(val))) if val is not None else None


def extraire_tours(raw):
    laps = (raw.get("splits") or {}).get("lapDTOs") or []
    tours = []
    for lap in laps:
        duree = lap.get("duration") or 0
        distance = lap.get("distance") or 0
        if duree < TOUR_MIN_SECONDES and distance < TOUR_MIN_METRES:
            continue
        tours.append({
            "index": len(tours) + 1,
            "distanceKm": _arrondi(distance / 1000.0, 3),
            "dureeSec": _entier(duree),
            "allureSecKm": _allure(lap.get("averageSpeed")),
            "gapSecKm": _allure(lap.get("avgGradeAdjustedSpeed")),
            "fcMoy": _entier(lap.get("averageHR")),
            "fcMax": _entier(lap.get("maxHR")),
            "cadenceMoy": _entier(lap.get("averageRunCadence")),
            "puissanceMoy": _entier(lap.get("averagePower")),
            "denivelePosM": _entier(lap.get("elevationGain")),
            "deniveleNegM": _entier(lap.get("elevationLoss")),
            "intensite": lap.get("intensityType"),
        })
    return tours


def extraire_zones_fc(raw):
    zones = raw.get("zonesFC") or []
    out = []
    for z in zones:
        if not isinstance(z, dict):
            continue
        secs = z.get("secsInZone") or 0
        out.append({
            "zone": z.get("zoneNumber"),
            "secondes": _entier(secs),
            "borneBasse": _entier(z.get("zoneLowBoundary")),
        })
    out.sort(key=lambda z: z["zone"] or 0)
    return out


def extraire_meteo(raw):
    m = raw.get("meteo")
    if not isinstance(m, dict) or not m:
        return None
    if "erreur" in m:
        return None
    return {
        "temperatureC": _f_vers_c(m.get("temp")),
        "ressentiC": _f_vers_c(m.get("apparentTemp")),
        "humidite": _entier(m.get("relativeHumidity")),
        "ventKmh": _mph_vers_kmh(m.get("windSpeed")),
        "description": (m.get("weatherTypeDTO") or {}).get("desc"),
        "station": (m.get("weatherStationDTO") or {}).get("name"),
    }


def extraire_fiche(raw):
    """Fiche compacte d'une activité à partir de son JSON brut."""
    resume = raw.get("resume") or {}
    summary = (raw.get("detail") or {}).get("summaryDTO") or {}

    def val(*cles):
        """Première valeur non nulle, en cherchant dans summary puis resume."""
        for c in cles:
            for source in (summary, resume):
                if source.get(c) is not None:
                    return source[c]
        return None

    # Format ISO avec "T" : c'est la clé de liaison avec `dateHeure` dans
    # activites.json, produit par build_data.parse_date().
    brut_date = str(resume.get("startTimeLocal") or summary.get("startTimeLocal") or "")
    date_heure = brut_date.replace(" ", "T")[:19]
    distance_m = val("distance")

    return {
        "activityId": raw.get("activityId"),
        "dateHeure": date_heure or None,
        "date": date_heure[:10] or None,
        "titre": resume.get("activityName") or (raw.get("detail") or {}).get("activityName"),
        "type": ((resume.get("activityType") or {}).get("typeKey")),
        "lieu": (raw.get("detail") or {}).get("locationName"),
        "distanceKm": _arrondi((distance_m or 0) / 1000.0, 2) if distance_m else None,
        "dureeSec": _entier(val("duration")),
        "allureMoySecKm": _allure(val("averageSpeed")),
        "gapMoySecKm": _allure(val("avgGradeAdjustedSpeed")),
        "fcMoy": _entier(val("averageHR")),
        "fcMax": _entier(val("maxHR")),
        "fcMin": _entier(summary.get("minHR")),
        "cadenceMoy": _entier(val("averageRunCadence", "averageRunningCadenceInStepsPerMinute")),
        "denivelePosM": _entier(val("elevationGain")),
        "deniveleNegM": _entier(val("elevationLoss")),
        "calories": _entier(val("calories")),
        "teAerobie": _arrondi(val("trainingEffect", "aerobicTrainingEffect"), 1),
        "teAnaerobie": _arrondi(val("anaerobicTrainingEffect"), 1),
        "teLabel": summary.get("trainingEffectLabel"),
        "chargeEntrainement": _arrondi(val("activityTrainingLoad"), 1),
        "rpe": summary.get("directWorkoutRpe"),
        "ressenti": summary.get("directWorkoutFeel"),
        "tours": extraire_tours(raw),
        "zonesFC": extraire_zones_fc(raw),
        "meteo": extraire_meteo(raw),
    }


def charger_fiches(data_dir):
    """Toutes les fiches d'une prépa, indexées par dateHeure (clé de liaison
    avec activites.json, le CSV Garmin ne portant pas d'activityId)."""
    raw_dir = Path(data_dir) / "garmin_raw"
    if not raw_dir.is_dir():
        return {}
    fiches = {}
    for chemin in sorted(raw_dir.glob("*.json")):
        try:
            raw = json.loads(chemin.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError):
            continue
        fiche = extraire_fiche(raw)
        if fiche.get("dateHeure"):
            fiches[fiche["dateHeure"]] = fiche
    return fiches
