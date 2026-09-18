#!/usr/bin/env python3
"""Migration des donnees de l'ancienne application vers l'API.

Lit l'arborescence `profiles/<athlete>/prepas/<prepa>/data/` de l'ancienne application et
pousse son contenu par l'API, pour que la migration passe par les memes regles de
validation et de deduplication que l'usage courant — plutot que d'ecrire en base.

Le dossier source se designe par `--source` : cette application ne le contient plus, il
faut donc pointer vers une copie de l'ancienne arborescence.

Ce qui est repris :

  profile.json      -> compte athlete (le mot de passe est demande ou genere)
  objectifs.json    -> cycle de type PREPA, profil sportif, records, blessures,
                       contraintes, seances signature
  garmin.csv        -> activites, via l'import CSV de l'API
  plan.json         -> semaines et seances planifiees
  journal.md        -> entrees de journal, datees au lundi de leur semaine
  etat_analyse.json -> activites deja passees en revue

Le champ `commentairesLibres` n'est volontairement pas importe tel quel : c'est
un texte cumulatif de plusieurs milliers de caracteres qu'il faut relire et
decouper en notes de coach. Le script l'ecrit dans un fichier a part, a traiter
a la main.

Usage :
    python3 migration/import_legacy.py --source /chemin/vers/profiles \
        --api http://localhost:8080 --key psk_… --admin-password …
    python3 migration/import_legacy.py --source /chemin/vers/profiles --profil thibaut --dry-run
"""

import argparse
import getpass
import json
import re
import secrets
import sys
import urllib.error
import urllib.request
from datetime import date, datetime, timedelta
from pathlib import Path

RACINE = Path(__file__).resolve().parent.parent
SORTIE = RACINE / "migration" / "sortie"

# Renseigne par --source : l'arborescence `profiles/` de l'ancienne application.
PROFILES = None

# L'ancien plan nommait les seances en libelles mixtes ; le nouveau modele a un enum.
TYPES_SEANCE = {
    "EF": "EF",
    "SL": "SL",
    "Seuil": "SEUIL",
    "VMA": "VMA",
    "AM": "AM",
    "Cotes": "COTES",
    "Côtes": "COTES",
    "Renfo": "RENFO",
    "Marathon": "COURSE",
    "Course": "COURSE",
    "Cross": "CROSS",
    "Repos": "REPOS",
}

# Une seance deja validee par l'ancien coach a bien ete passee en revue : elle devient
# ANALYSEE. Une seance manquee devient le constat correspondant — la raison, elle, vit
# dans le commentaire du coach.
STATUTS = {"a_venir": "A_VENIR", "validee": "ANALYSEE", "manquee": "NON_REALISEE"}

# Les blocs etaient des libelles libres ; on les rattache aux phases connues.
BLOCS = [
    ("affûtage", "AFFUTAGE"),
    ("affutage", "AFFUTAGE"),
    ("spécifique", "SPECIFIQUE"),
    ("specifique", "SPECIFIQUE"),
    ("développement", "DEVELOPPEMENT"),
    ("developpement", "DEVELOPPEMENT"),
    ("décharge", "DECHARGE"),
    ("decharge", "DECHARGE"),
    ("base", "BASE"),
    ("fondation", "BASE"),
]

DISTANCES_RECORD = {
    "rp5km": 5000,
    "rp10km": 10000,
    "semi": 21097,
    "marathon": 42195,
}


# ---------------------------------------------------------------------------
# Dumps Garmin : tours, zones de frequence cardiaque, meteo
# ---------------------------------------------------------------------------

# Un tour plus court que cela est un artefact — arret de montre, tour fantome de fin
# d'activite — et non une portion de seance.
TOUR_MIN_SECONDES = 10
TOUR_MIN_METRES = 50

# L'API Garmin sert la meteo en unites imperiales quelle que soit la langue du compte.
def fahrenheit_vers_celsius(f):
    return round((float(f) - 32) * 5 / 9, 1) if f is not None else None


def mph_vers_kmh(mph):
    return round(float(mph) * 1.609, 1) if mph is not None else None


def allure_depuis_vitesse(vitesse_ms):
    """Vitesse en metres par seconde vers une allure en secondes par kilometre."""
    if not vitesse_ms:
        return None
    secondes = 1000.0 / float(vitesse_ms)
    # Hors de ces bornes, la valeur est aberrante : montre a l'arret ou pic de GPS.
    return int(round(secondes)) if 100 < secondes < 3600 else None


def entier(valeur):
    return int(round(float(valeur))) if valeur is not None else None


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
        if not isinstance(zone, dict):
            continue
        zones.append({
            "zone": zone.get("zoneNumber"),
            "secondes": entier(zone.get("secsInZone") or 0),
            "borneBasse": entier(zone.get("zoneLowBoundary")),
        })
    zones.sort(key=lambda z: z["zone"] or 0)
    return zones


def extraire_meteo(brut):
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


def extraire_activite(brut):
    """Un dump brut vers le format d'ingestion de l'API."""
    resume = brut.get("resume") or {}
    summary = (brut.get("detail") or {}).get("summaryDTO") or {}

    def valeur(*cles):
        """Premiere valeur non nulle, cherchee dans le resume detaille puis dans le resume."""
        for cle in cles:
            for source in (summary, resume):
                if source.get(cle) is not None:
                    return source[cle]
        return None

    depart = str(resume.get("startTimeLocal") or summary.get("startTimeLocal") or "")
    depart = depart.replace(" ", "T")[:19]
    if not depart:
        return None

    return {
        "garminActivityId": brut.get("activityId"),
        "source": "GARMIN_API",
        "type": TYPES_GARMIN.get((resume.get("activityType") or {}).get("typeKey"), "OTHER"),
        "typeGarmin": (resume.get("activityType") or {}).get("typeKey"),
        "titre": resume.get("activityName") or (brut.get("detail") or {}).get("activityName"),
        "lieu": (brut.get("detail") or {}).get("locationName"),
        "startedAtLocal": depart,
        "dureeSec": entier(valeur("duration")) or 0,
        "distanceM": entier(valeur("distance")),
        "allureMoySecKm": allure_depuis_vitesse(valeur("averageSpeed")),
        "gapMoySecKm": allure_depuis_vitesse(valeur("avgGradeAdjustedSpeed")),
        "fcMoy": entier(valeur("averageHR")),
        "fcMax": entier(valeur("maxHR")),
        "fcMin": entier(summary.get("minHR")),
        "cadenceMoy": entier(valeur("averageRunCadence", "averageRunningCadenceInStepsPerMinute")),
        "denivelePosM": entier(valeur("elevationGain")),
        "deniveleNegM": entier(valeur("elevationLoss")),
        "calories": entier(valeur("calories")),
        "teAerobie": valeur("trainingEffect", "aerobicTrainingEffect"),
        "teAnaerobie": valeur("anaerobicTrainingEffect"),
        "teLabel": summary.get("trainingEffectLabel"),
        "chargeEntrainement": valeur("activityTrainingLoad"),
        "tours": extraire_tours(brut),
        "zonesFc": extraire_zones_fc(brut),
        "meteo": extraire_meteo(brut),
    }


# Cles techniques Garmin vers les types de l'application.
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


class Api:
    """Client minimal de l'API.

    Deux identites coexistent, comme a l'usage : la cle de service porte la voix du coach
    pour tout ce qui touche au plan et aux activites, et un compte administrateur — le seul
    a pouvoir creer des comptes — sert pour les quelques appels d'administration.
    """

    def __init__(self, base, cle, jeton_admin=None, dry_run=False):
        self.base = base.rstrip("/")
        self.cle = cle
        self.jeton_admin = jeton_admin
        self.dry_run = dry_run

    @classmethod
    def connecter_admin(cls, base, email, mot_de_passe):
        """Ouvre une session administrateur et renvoie son jeton d'acces."""
        requete = urllib.request.Request(
            f"{base.rstrip('/')}/api/v1/auth/login",
            data=json.dumps({"identifiant": email, "motDePasse": mot_de_passe}).encode(),
            method="POST")
        requete.add_header("Content-Type", "application/json")
        with urllib.request.urlopen(requete) as reponse:
            return json.loads(reponse.read())["accessToken"]

    def appeler(self, methode, chemin, corps=None, fichier=None):
        url = f"{self.base}/api/v1{chemin}"
        if self.dry_run:
            print(f"    [dry-run] {methode} {chemin}")
            return {}
        if fichier is not None:
            data, content_type = _multipart(fichier)
        else:
            data = json.dumps(corps).encode() if corps is not None else None
            content_type = "application/json"
        requete = urllib.request.Request(url, data=data, method=methode)
        if chemin.startswith("/admin/"):
            requete.add_header("Authorization", f"Bearer {self.jeton_admin}")
        else:
            requete.add_header("X-Service-Key", self.cle)
        if data is not None:
            requete.add_header("Content-Type", content_type)
        try:
            with urllib.request.urlopen(requete) as reponse:
                contenu = reponse.read()
                return json.loads(contenu) if contenu else {}
        except urllib.error.HTTPError as e:
            detail = e.read().decode(errors="replace")
            raise RuntimeError(f"{methode} {chemin} -> {e.code} {detail}") from e


def _multipart(chemin):
    """Encodage multipart d'un seul fichier, sans dependance externe."""
    frontiere = "----prepa" + secrets.token_hex(8)
    corps = b"".join([
        f"--{frontiere}\r\n".encode(),
        f'Content-Disposition: form-data; name="file"; filename="{chemin.name}"\r\n'.encode(),
        b"Content-Type: text/csv\r\n\r\n",
        chemin.read_bytes(),
        f"\r\n--{frontiere}--\r\n".encode(),
    ])
    return corps, f"multipart/form-data; boundary={frontiere}"


def bloc_normalise(libelle):
    if not libelle:
        return None
    minuscule = libelle.lower()
    for motif, valeur in BLOCS:
        if motif in minuscule:
            return valeur
    return None


def lundi_de(jour):
    return jour - timedelta(days=jour.weekday())


def parse_periode(texte):
    """Extrait un couple de dates d'une periode ecrite « 2026-08-29 → 2026-09-09 »."""
    if not texte:
        return None, None
    dates = re.findall(r"\d{4}-\d{2}-\d{2}", texte)
    return (dates[0] if dates else None, dates[1] if len(dates) > 1 else None)


# Une reference peut dire qu'elle n'existe pas : « Pas de RP semi ». Le chiffre qui suit
# est alors une allure ou une distance, pas un temps de course.
NEGATIONS = ("pas de", "aucun", "jamais", "non couru", "non renseign")


def allure_plausible(temps_sec, distance_m):
    """Garde-fou : un record doit correspondre a une allure de course credible."""
    allure = temps_sec / (distance_m / 1000)
    return 150 <= allure <= 720


def parse_temps(texte):
    """Lit un temps « 47:50 », « 1h35 » ou « 3h58:20 » en secondes."""
    if not texte:
        return None
    if any(negation in texte.lower() for negation in NEGATIONS):
        return None
    m = re.search(r"(\d+)\s*h\s*(\d{1,2})?(?::(\d{2}))?", texte)
    if m:
        heures = int(m.group(1))
        minutes = int(m.group(2) or 0)
        secondes = int(m.group(3) or 0)
        return heures * 3600 + minutes * 60 + secondes
    m = re.search(r"(?<!\d)(\d{1,3}):(\d{2})(?!\d)", texte)
    if m:
        return int(m.group(1)) * 60 + int(m.group(2))
    return None


def parse_date_dans(texte):
    """Trouve une date dans un texte libre, au format ISO ou JJ/MM/AAAA."""
    if not texte:
        return None
    m = re.search(r"(\d{4})-(\d{2})-(\d{2})", texte)
    if m:
        return m.group(0)
    m = re.search(r"(\d{2})/(\d{2})/(\d{4})", texte)
    if m:
        return f"{m.group(3)}-{m.group(2)}-{m.group(1)}"
    return None


def parser_journal(texte, semaines_par_numero):
    """Decoupe le journal en entrees et les date au lundi de la semaine concernee."""
    entrees = []
    for bloc in re.split(r"^##\s+", texte, flags=re.MULTILINE):
        bloc = bloc.strip()
        if not bloc:
            continue
        lignes = bloc.split("\n", 1)
        titre = lignes[0].strip()
        contenu = lignes[1].strip() if len(lignes) > 1 else ""
        if not contenu:
            continue
        m = re.search(r"[Ss]emaine\s+(\d+)", titre)
        if not m:
            continue
        numero = int(m.group(1))
        debut = semaines_par_numero.get(numero)
        if not debut:
            continue
        entrees.append({
            "date": debut,
            "contenu": f"## {titre}\n\n{contenu}",
            "douleur": bool(re.search(r"douleur|gêne|gene|blessure", contenu, re.I)),
        })
    return entrees


class Migration:
    def __init__(self, api, profil, prepa_slug=None):
        self.api = api
        self.profil = profil
        self.dossier = PROFILES / profil
        self.profile_json = json.loads((self.dossier / "profile.json").read_text(encoding="utf-8"))
        self.prepa_slug = prepa_slug or self.profile_json.get("prepaActive")
        self.data = self.dossier / "prepas" / self.prepa_slug / "data"
        self.objectifs = self._lire("objectifs.json") or {}
        self.plan = self._lire("plan.json") or {}
        self.athlete_id = None
        self.cycle_id = None
        self.rapport = {"profil": profil, "prepa": self.prepa_slug}

    def _lire(self, nom):
        chemin = self.data / nom
        return json.loads(chemin.read_text(encoding="utf-8")) if chemin.exists() else None

    def executer(self, mot_de_passe):
        print(f"\n== {self.profil} / {self.prepa_slug} ==")
        self.creer_athlete(mot_de_passe)
        self.importer_profil_sportif()
        self.importer_activites()
        self.importer_details()
        self.creer_cycle()
        self.importer_plan()
        self.importer_journal()
        self.importer_etat_analyse()
        self.rattacher_activites()
        self.extraire_memoire_coach()
        return self.rapport

    def creer_athlete(self, mot_de_passe):
        nom = self.profile_json.get("nom", self.profil)
        email = f"{self.profil}@prepa.local"
        reponse = self.api.appeler("POST", "/admin/athletes", {
            "email": email,
            "motDePasse": mot_de_passe,
            "displayName": nom,
        })
        self.athlete_id = reponse.get("id")
        self.rapport["athleteId"] = self.athlete_id
        self.rapport["email"] = email
        print(f"  athlete   : {nom} <{email}>")

    def importer_profil_sportif(self):
        """Les donnees de fond quittent le cycle pour rejoindre l'athlete."""
        renfo = self.objectifs.get("renforcement") or {}
        self.api.appeler("PUT", f"/athletes/{self.athlete_id}/profile", {
            "volumeHabituelKm": self.objectifs.get("volumeDepartKmSemaine"),
            "renfoActif": bool(renfo.get("actif")),
            "renfoFrequence": renfo.get("frequenceParSemaine"),
            "renfoMateriel": renfo.get("materiel") or [],
            "renfoFocus": renfo.get("focus"),
        })

        references = self.objectifs.get("references") or {}
        records = 0
        for cle, distance in DISTANCES_RECORD.items():
            temps = parse_temps(references.get(cle))
            if not temps or not allure_plausible(temps, distance):
                continue
            self.api.appeler("POST", f"/athletes/{self.athlete_id}/records", {
                "distanceM": distance,
                "tempsSec": temps,
                "date": parse_date_dans(references[cle]) or date.today().isoformat(),
                "contexte": references[cle][:300],
            })
            records += 1

        blessures = 0
        for blessure in self.objectifs.get("blessures") or []:
            statut = (blessure.get("statut") or "").lower()
            self.api.appeler("POST", f"/athletes/{self.athlete_id}/injuries", {
                "zone": blessure.get("zone", "Non precisee")[:200],
                "statut": "RESOLUE" if "résolu" in statut or "resolu" in statut
                          else "SURVEILLANCE" if "surveill" in statut or "légère" in statut
                          else "ACTIVE",
                "consignes": (blessure.get("consignes") or blessure.get("statut") or "")[:2000],
                "debut": self.objectifs.get("dateDebutPrepa") or date.today().isoformat(),
            })
            blessures += 1

        contraintes = 0
        for contrainte in self.objectifs.get("contraintes") or []:
            debut, fin = parse_periode(contrainte.get("periode"))
            type_brut = (contrainte.get("type") or "autre").upper()
            self.api.appeler("POST", f"/athletes/{self.athlete_id}/constraints", {
                "type": type_brut if type_brut in ("VACANCES", "METEO", "MATERIEL", "PRO") else "AUTRE",
                "debut": debut,
                "fin": fin,
                "detail": (contrainte.get("detail") or contrainte.get("periode") or "")[:2000],
            })
            contraintes += 1

        signatures = 0
        for favorite in self.objectifs.get("seancesFavorites") or []:
            self.api.appeler("POST", f"/athletes/{self.athlete_id}/signature-sessions", {
                "nom": favorite.get("nom", "Seance signature")[:200],
                "typeSeance": TYPES_SEANCE.get(favorite.get("type"), "EF"),
                "description": (favorite.get("description") or "")[:2000],
                "distanceKm": favorite.get("distanceKm"),
                "frequenceSouhaitee": favorite.get("frequenceSouhaitee"),
                "contexte": (favorite.get("contexte") or "")[:2000],
            })
            signatures += 1

        self.rapport |= {"records": records, "blessures": blessures,
                         "contraintes": contraintes, "seancesSignature": signatures}
        print(f"  profil    : {records} records, {blessures} blessures, "
              f"{contraintes} contraintes, {signatures} seances signature")

    def importer_activites(self):
        csv = self.data / "garmin.csv"
        if not csv.exists():
            self.rapport["activites"] = 0
            return
        reponse = self.api.appeler(
            "POST", f"/athletes/{self.athlete_id}/activities/import-csv", fichier=csv)
        resultat = reponse.get("resultat", {})
        self.rapport["activites"] = resultat.get("importees", 0)
        self.rapport["activitesAvertissements"] = len(reponse.get("avertissements", []))
        print(f"  activites : {resultat.get('importees', 0)} importees, "
              f"{len(reponse.get('avertissements', []))} avertissements")

    def importer_details(self):
        """Ajoute tours, zones de frequence cardiaque et meteo aux activites deja importees.

        Le CSV ne porte ni tours ni meteo : ces donnees ne vivent que dans les dumps de
        l'API, archives seance par seance. C'est aussi par eux que les activites recuperent
        leur identifiant Garmin, absent de l'export.
        """
        dossier = self.data / "garmin_raw"
        if not dossier.is_dir():
            self.rapport["details"] = 0
            return

        lot = []
        for chemin in sorted(dossier.glob("*.json")):
            try:
                brut = json.loads(chemin.read_text(encoding="utf-8"))
            except (OSError, json.JSONDecodeError):
                continue
            activite = extraire_activite(brut)
            if activite:
                lot.append(activite)

        if not lot:
            self.rapport["details"] = 0
            return

        resultat = self.api.appeler(
            "POST", f"/athletes/{self.athlete_id}/activities/ingest", lot)
        self.rapport["details"] = resultat.get("misesAJour", 0)
        self.rapport["detailsNouvelles"] = resultat.get("importees", 0)
        print(f"  details   : {resultat.get('misesAJour', 0)} seances enrichies, "
              f"{resultat.get('importees', 0)} ajoutees, {resultat.get('doublons', 0)} inchangees")

    def creer_cycle(self):
        course = self.objectifs.get("course") or self.plan.get("course") or {}
        distance_km = course.get("distanceKm")
        reponse = self.api.appeler("POST", f"/athletes/{self.athlete_id}/cycles", {
            "slug": self.prepa_slug,
            "nom": course.get("nom") or self.prepa_slug,
            "type": "PREPA",
            "dateDebut": self.objectifs.get("dateDebutPrepa") or self.plan.get("dateDebutPrepa"),
            "dateFin": course.get("date"),
            "courseNom": course.get("nom"),
            "courseDate": course.get("date"),
            "courseDistanceM": int(distance_km * 1000) if distance_km else None,
            "chronoViseSec": self.objectifs.get("chronoViseSec"),
            "alluresCibles": self.objectifs.get("alluresCibles") or {},
            "activer": True,
        })
        self.cycle_id = reponse.get("id")
        self.rapport["cycleId"] = self.cycle_id
        print(f"  cycle     : {course.get('nom')} le {course.get('date')}")

    def importer_plan(self):
        semaines = []
        nb_seances = 0
        for semaine in self.plan.get("semaines") or []:
            seances = []
            for seance in semaine.get("seances") or []:
                type_seance = TYPES_SEANCE.get(seance.get("type"), "EF")
                focus = seance.get("focus")
                if type_seance == "RENFO" and not focus:
                    # Le contrat impose un focus ; l'ancien plan le laissait parfois vide.
                    focus = seance.get("titre") or "Renforcement general"
                seances.append({
                    "date": seance.get("date"),
                    "type": type_seance,
                    "titre": (seance.get("titre") or "Seance")[:300],
                    "description": seance.get("description"),
                    "statut": STATUTS.get(seance.get("statut"), "A_VENIR"),
                    "alluresTexte": seance.get("alluresCibles"),
                    "distanceCibleKm": seance.get("distanceCibleKm"),
                    "dureeCibleMin": seance.get("dureeCibleMin"),
                    "focus": focus,
                    "commentaireCoach": seance.get("commentaireCoach"),
                })
                nb_seances += 1
            semaines.append({
                "numero": semaine.get("numero"),
                "dateDebut": semaine.get("dateDebut"),
                "bloc": bloc_normalise(semaine.get("bloc")),
                "volumeCibleKm": semaine.get("volumeCibleKm") or 0,
                "detaillee": True,
                "note": semaine.get("note"),
                "seances": seances,
            })
        if semaines:
            self.api.appeler("PUT", f"/cycles/{self.cycle_id}/plan", {"semaines": semaines})
        self.rapport |= {"semaines": len(semaines), "seances": nb_seances}
        print(f"  plan      : {len(semaines)} semaines, {nb_seances} seances")

    def importer_journal(self):
        chemin = self.data / "journal.md"
        if not chemin.exists():
            self.rapport["journal"] = 0
            return
        semaines = {s.get("numero"): s.get("dateDebut") for s in self.plan.get("semaines") or []}
        entrees = parser_journal(chemin.read_text(encoding="utf-8"), semaines)
        for entree in entrees:
            self.api.appeler("PUT", f"/athletes/{self.athlete_id}/journal", entree)
        self.rapport["journal"] = len(entrees)
        print(f"  journal   : {len(entrees)} entrees")

    def importer_etat_analyse(self):
        """Les seances deja passees en revue le restent : le coach ne les reanalysera pas."""
        etat = self._lire("etat_analyse.json") or {}
        analysees = etat.get("analysees") or {}
        if not analysees:
            self.rapport["dejaAnalysees"] = 0
            return
        activites = self.api.appeler("GET", f"/athletes/{self.athlete_id}/activities") or []
        par_garmin = {str(a.get("garminActivityId")): a["id"] for a in activites if a.get("garminActivityId")}
        par_date = {}
        for a in activites:
            par_date.setdefault(a["date"], a["id"])

        ids = []
        for garmin_id, detail in analysees.items():
            identifiant = par_garmin.get(str(garmin_id)) or par_date.get(detail.get("date"))
            if identifiant:
                ids.append(identifiant)
        if ids:
            self.api.appeler("POST", f"/athletes/{self.athlete_id}/activities/mark-analyzed",
                             {"activityIds": sorted(set(ids))})
        self.rapport["dejaAnalysees"] = len(set(ids))
        print(f"  analysees : {len(set(ids))} activites deja passees en revue")

    def rattacher_activites(self):
        """Rejoue le rattachement des activites aux seances, plan une fois en place.

        Les activites sont importees avant que le cycle et son plan n'existent : le constat
        pose a l'ingestion n'avait donc aucune seance a rattacher, et tout le passe restait
        « a venir ». On le rejoue ici, une fois le plan ecrit.
        """
        debut = self.plan.get("dateDebut") or self.objectifs.get("dateDebut")
        chemin = f"/athletes/{self.athlete_id}/reconcile"
        if debut:
            chemin += f"?debut={debut}"
        resultat = self.api.appeler("POST", chemin) or {}
        print(f"  constats  : {resultat.get('seancesConstatees', 0)} seances tranchees")
        self.rapport["seancesConstatees"] = resultat.get("seancesConstatees", 0)

    def extraire_memoire_coach(self):
        """Depose le texte cumulatif a relire : il devient des notes de coach a la main."""
        texte = self.objectifs.get("commentairesLibres")
        if not texte:
            return
        SORTIE.mkdir(parents=True, exist_ok=True)
        chemin = SORTIE / f"memoire-a-relire-{self.profil}.md"
        chemin.write_text(
            f"# Memoire de coach a decouper — {self.profil}\n\n"
            f"athleteId : {self.athlete_id}\ncycleId : {self.cycle_id}\n\n"
            "Ce texte vient de `objectifs.commentairesLibres`. Il doit etre relu et decoupe en\n"
            "notes de coach (portee DURABLE, CYCLE ou PONCTUELLE), chacune de 500 caracteres au plus.\n\n"
            "---\n\n" + texte,
            encoding="utf-8")
        self.rapport["memoireACouper"] = str(chemin.relative_to(RACINE))
        print(f"  memoire   : {len(texte)} caracteres a relire -> {chemin.name}")


def main():
    parser = argparse.ArgumentParser(description="Migration des donnees vers l'API")
    parser.add_argument("--source", required=True, type=Path,
                        help="dossier profiles/ de l'ancienne application")
    parser.add_argument("--api", default="http://localhost:8080")
    parser.add_argument("--key", help="cle de service (X-Service-Key)")
    parser.add_argument("--admin-email", default="admin@prepa.local")
    parser.add_argument("--admin-password",
                        help="mot de passe du compte administrateur ; demande a la saisie s'il est absent")
    parser.add_argument("--profil", action="append", help="profil a migrer, repetable")
    parser.add_argument("--mot-de-passe", help="mot de passe des comptes crees")
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()

    if not args.dry_run:
        if not args.key:
            parser.error("--key est requis (ou --dry-run)")
        # Un mot de passe passe en argument reste dans l'historique du shell et s'affiche
        # dans la liste des processus. On le demande plutot que de l'exiger sur la ligne
        # de commande.
        if not args.admin_password:
            args.admin_password = getpass.getpass(f"Mot de passe de {args.admin_email} : ")
        if not args.admin_password:
            parser.error("mot de passe administrateur vide")

    global PROFILES
    PROFILES = args.source.expanduser().resolve()
    if not PROFILES.is_dir():
        parser.error(f"Dossier source introuvable : {PROFILES}")

    profils = args.profil or sorted(p.name for p in PROFILES.iterdir() if (p / "profile.json").exists())
    jeton = None if args.dry_run else Api.connecter_admin(args.api, args.admin_email, args.admin_password)
    api = Api(args.api, args.key or "", jeton, args.dry_run)
    mot_de_passe = args.mot_de_passe or secrets.token_urlsafe(12)

    rapports = []
    for profil in profils:
        try:
            rapports.append(Migration(api, profil).executer(mot_de_passe))
        except Exception as e:  # une migration ratee ne doit pas emporter les autres
            print(f"  ECHEC {profil} : {e}", file=sys.stderr)
            rapports.append({"profil": profil, "erreur": str(e)})

    SORTIE.mkdir(parents=True, exist_ok=True)
    rapport = {"le": datetime.now().isoformat(timespec="seconds"), "profils": rapports}
    (SORTIE / "rapport-migration.json").write_text(
        json.dumps(rapport, ensure_ascii=False, indent=2), encoding="utf-8")

    print(f"\nMot de passe des comptes : {mot_de_passe}")
    print(f"Rapport : migration/sortie/rapport-migration.json")
    return 1 if any("erreur" in r for r in rapports) else 0


if __name__ == "__main__":
    sys.exit(main())
