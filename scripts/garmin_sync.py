#!/usr/bin/env python3
"""garmin_sync.py — récupère les activités Garmin Connect et les ajoute à garmin.csv.

Remplace la copie manuelle depuis l'app Garmin. Écrit en append dans le
`garmin.csv` de la prépa ciblée, au format exact de l'export Garmin (mêmes
en-têtes que le fichier existant), donc `build_data.py` reste inchangé.

Optionnellement (`--details`), archive pour chaque activité un JSON brut riche
dans `data/garmin_raw/<activityId>.json` : splits par km, temps par zone FC,
training effect aéro/anaéro, météo. Données non exposées dans l'export CSV.

Auth, par profil : GARMIN_EMAIL / GARMIN_PASSWORD dans `profiles/<profil>/.env`
(un compte Garmin par athlète). À défaut, le `.env` de la racine sert de repli
pour une installation mono-athlète.

Les tokens OAuth sont mis en cache dans ~/.garminconnect/<profil> (~1 an, un
dossier par athlète pour que deux profils ne s'écrasent pas), donc le mot de
passe et le MFA ne sont redemandés qu'exceptionnellement.

Garde-fou : le compte Garmin connecté est comparé au `garminDisplayName`
enregistré dans `profile.json` ; en cas de non-correspondance le sync s'arrête
plutôt que d'écrire les activités d'un athlète dans le CSV d'un autre.

Nécessite le venv du projet :
    .venv/bin/python scripts/garmin_sync.py --profil thibaut --prepa marathon-2026-10

Usage :
    ... --profil thibaut --prepa marathon-2026-10                 # depuis la dernière activité connue
    ... --profil thibaut --prepa marathon-2026-10 --depuis 2026-09-01
    ... --profil thibaut --prepa marathon-2026-10 --details       # + JSON enrichi
    ... --profil thibaut --prepa marathon-2026-10 --backfill      # rattrape les détails manquants depuis le début de la prépa
    ... --profil thibaut --prepa marathon-2026-10 --dry-run       # aperçu, n'écrit rien
"""

import argparse
import csv
import json
import os
import sys
import time
from datetime import date, datetime, timedelta
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PROFILES_DIR = ROOT / "profiles"
TOKENSTORE_BASE = Path(os.getenv("GARMINTOKENS", "~/.garminconnect")).expanduser()

# En-têtes utilisés si le garmin.csv n'existe pas encore (superset le plus riche).
HEADER_DEFAUT = [
    "Type d'activité", "Date", "Favori", "Titre", "Distance", "Calories", "Durée",
    "Fréquence cardiaque moyenne", "Fréquence cardiaque maximale", "TE aérobie",
    "Cadence de course moyenne", "Cadence de course maximale", "Allure moyenne",
    "Meilleure allure", "Ascension totale", "Descente totale",
    "Longueur moyenne des foulées", "Rapport vertical moyen",
    "Oscillation verticale moyenne", "Temps de contact moyen avec le sol",
    "GAP moyenne", "Normalized Power® (NP®)", "Training Stress Score® (TSS®)",
    "Puissance moyenne", "Puissance max.", "Pas", "Consommation du Body Battery",
    "Décompression", "Temps du meilleur circuit", "Nombre de tours",
    "Stress moyen", "Stress maximal", "Temps de déplacement", "Temps écoulé",
    "Altitude minimale", "Altitude maximale",
]

# typeKey Garmin -> libellé FR tel qu'écrit par l'export officiel.
TYPES_FR = {
    "running": "Course à pied",
    "trail_running": "Trail",
    "track_running": "Course à pied sur piste",
    "treadmill_running": "Course sur tapis roulant",
    "indoor_running": "Course à pied en intérieur",
    "obstacle_run": "Course d'obstacles",
    "cycling": "Cyclisme",
    "road_biking": "Vélo de route",
    "mountain_biking": "VTT",
    "gravel_cycling": "Vélo gravel",
    "indoor_cycling": "Vélo en intérieur",
    "virtual_ride": "Sortie virtuelle",
    "walking": "Marche à pied",
    "hiking": "Randonnée",
    "strength_training": "Musculation",
    "indoor_cardio": "Cardio en intérieur",
    "yoga": "Yoga",
    "pilates": "Pilates",
    "lap_swimming": "Natation en piscine",
    "open_water_swimming": "Natation en eau libre",
    "elliptical": "Vélo elliptique",
    "rowing": "Aviron",
    "indoor_rowing": "Aviron en intérieur",
    "stair_climbing": "Montée d'escaliers",
    "fitness_equipment": "Appareil de fitness",
    "multi_sport": "Multisport",
    "other": "Autre",
}


# ---------------------------------------------------------------------------
# Formatage vers le format de l'export Garmin
# ---------------------------------------------------------------------------

def f_num(val, dec=2):
    """Nombre décimal, point comme séparateur (comme l'export Garmin)."""
    if val is None:
        return None
    return f"{float(val):.{dec}f}"


def f_int(val):
    if val is None:
        return None
    return str(int(round(float(val))))


def f_duree(secondes):
    """Secondes -> HH:MM:SS."""
    if secondes is None:
        return None
    total = int(round(float(secondes)))
    h, reste = divmod(total, 3600)
    m, s = divmod(reste, 60)
    return f"{h:02d}:{m:02d}:{s:02d}"


def f_allure(vitesse_ms):
    """Vitesse en m/s -> allure m:ss /km."""
    if not vitesse_ms:
        return None
    sec_km = 1000.0 / float(vitesse_ms)
    if sec_km > 3600:  # arrêt / valeur aberrante
        return None
    m, s = divmod(int(round(sec_km)), 60)
    return f"{m}:{s:02d}"


def f_date(act):
    """startTimeLocal -> 'YYYY-MM-DD HH:MM:SS'."""
    brut = act.get("startTimeLocal") or act.get("startTimeGMT")
    if not brut:
        return None
    return str(brut).replace("T", " ")[:19]


def type_fr(act):
    cle = ((act.get("activityType") or {}).get("typeKey")) or "other"
    return TYPES_FR.get(cle, cle.replace("_", " ").capitalize())


# Chaque en-tête FR -> fonction extrayant la valeur depuis l'activité API.
EXTRACTEURS = {
    "Type d'activité": type_fr,
    "Date": f_date,
    "Favori": lambda a: "true" if a.get("favorite") else "false",
    "Titre": lambda a: a.get("activityName") or "",
    "Distance": lambda a: f_num((a.get("distance") or 0) / 1000.0, 2) if a.get("distance") else None,
    "Calories": lambda a: f_int(a.get("calories")),
    "Durée": lambda a: f_duree(a.get("duration")),
    "Fréquence cardiaque moyenne": lambda a: f_int(a.get("averageHR")),
    "Fréquence cardiaque maximale": lambda a: f_int(a.get("maxHR")),
    "TE aérobie": lambda a: f_num(a.get("aerobicTrainingEffect"), 1),
    "TE anaérobie": lambda a: f_num(a.get("anaerobicTrainingEffect"), 1),
    "Cadence de course moyenne": lambda a: f_int(a.get("averageRunningCadenceInStepsPerMinute")),
    "Cadence de course maximale": lambda a: f_int(a.get("maxRunningCadenceInStepsPerMinute")),
    "Allure moyenne": lambda a: f_allure(a.get("averageSpeed")),
    "Meilleure allure": lambda a: f_allure(a.get("maxSpeed")),
    "Ascension totale": lambda a: f_int(a.get("elevationGain")),
    "Descente totale": lambda a: f_int(a.get("elevationLoss")),
    "Longueur moyenne des foulées": lambda a: f_num((a.get("avgStrideLength") or 0) / 100.0, 2) if a.get("avgStrideLength") else None,
    "Rapport vertical moyen": lambda a: f_num(a.get("avgVerticalRatio"), 1),
    "Oscillation verticale moyenne": lambda a: f_num(a.get("avgVerticalOscillation"), 1),
    "Temps de contact moyen avec le sol": lambda a: f_int(a.get("avgGroundContactTime")),
    "GAP moyenne": lambda a: f_allure(a.get("avgGradeAdjustedSpeed")),
    "Normalized Power® (NP®)": lambda a: f_int(a.get("normPower")),
    "Training Stress Score® (TSS®)": lambda a: f_num(a.get("trainingStressScore"), 1),
    "Puissance moyenne": lambda a: f_int(a.get("avgPower")),
    "Puissance max.": lambda a: f_int(a.get("maxPower")),
    "Pas": lambda a: f_int(a.get("steps")),
    "Consommation du Body Battery": lambda a: f_int(a.get("differenceBodyBattery")),
    "Décompression": lambda a: "Non",
    "Temps du meilleur circuit": lambda a: f_duree(a.get("bestLapDuration")),
    "Nombre de tours": lambda a: f_int(a.get("lapCount")),
    "Stress moyen": lambda a: f_int(a.get("avgStress")),
    "Stress maximal": lambda a: f_int(a.get("maxStress")),
    "Temps de déplacement": lambda a: f_duree(a.get("movingDuration")),
    "Temps écoulé": lambda a: f_duree(a.get("elapsedDuration")),
    "Altitude minimale": lambda a: f_int(a.get("minElevation")),
    "Altitude maximale": lambda a: f_int(a.get("maxElevation")),
    "VO2 max": lambda a: f_num(a.get("vO2MaxValue"), 1),
    "Charge d'entraînement": lambda a: f_num(a.get("activityTrainingLoad"), 1),
}


def ligne_csv(act, entetes):
    """Construit la ligne (liste de valeurs) pour les en-têtes du fichier cible."""
    ligne = []
    for h in entetes:
        extracteur = EXTRACTEURS.get(h.strip())
        val = None
        if extracteur:
            try:
                val = extracteur(act)
            except (TypeError, ValueError, ZeroDivisionError):
                val = None
        ligne.append("--" if val in (None, "") else val)
    return ligne


# ---------------------------------------------------------------------------
# Lecture du CSV existant
# ---------------------------------------------------------------------------

def lire_existant(csv_path):
    """Retourne (entetes, dates_connues, derniere_date)."""
    if not csv_path.exists() or csv_path.stat().st_size == 0:
        return list(HEADER_DEFAUT), set(), None
    with csv_path.open(encoding="utf-8-sig", newline="") as f:
        reader = csv.DictReader(f)
        entetes = [h for h in (reader.fieldnames or []) if h]
        if not entetes:
            return list(HEADER_DEFAUT), set(), None
        dates = set()
        for row in reader:
            brut = (row.get("Date") or "").strip()
            if brut:
                dates.add(brut[:19])
    derniere = max((d[:10] for d in dates), default=None)
    return entetes, dates, derniere


def csv_a_saut_de_ligne_final(csv_path):
    if not csv_path.exists() or csv_path.stat().st_size == 0:
        return True
    with csv_path.open("rb") as f:
        f.seek(-1, os.SEEK_END)
        return f.read(1) in (b"\n", b"\r")


# ---------------------------------------------------------------------------
# Connexion Garmin
# ---------------------------------------------------------------------------

def charger_env(profil):
    """Charge les identifiants du profil, puis le `.env` racine en repli.

    `profiles/<profil>/.env` est prioritaire : le premier fichier lu gagne
    (setdefault), ce qui permet d'avoir un compte Garmin par athlète tout en
    gardant un `.env` racine pour une installation mono-athlète.
    """
    for env in (PROFILES_DIR / profil / ".env", ROOT / ".env"):
        if not env.exists():
            continue
        for ligne in env.read_text(encoding="utf-8").splitlines():
            ligne = ligne.strip()
            if not ligne or ligne.startswith("#") or "=" not in ligne:
                continue
            cle, _, val = ligne.partition("=")
            os.environ.setdefault(cle.strip(), val.strip().strip('"').strip("'"))


def verifier_identite(api, profil_path, memoriser=True):
    """Compare le compte Garmin connecté à celui enregistré pour le profil.

    Empêche d'écrire les activités d'un athlète dans le CSV d'un autre. Au
    premier sync, le compte est mémorisé dans `profile.json` — sauf en
    --dry-run, qui ne doit rien écrire du tout.
    """
    compte = getattr(api, "display_name", None)
    nom_affiche = getattr(api, "full_name", None) or compte
    if not compte:
        print("⚠️  Compte Garmin non identifiable, vérification d'identité ignorée.")
        return

    profile_json = profil_path / "profile.json"
    try:
        profile = json.loads(profile_json.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        print(f"⚠️  {profile_json} illisible, vérification d'identité ignorée.")
        return

    attendu = profile.get("garminDisplayName")
    if attendu and attendu != compte:
        sys.exit(
            f"❌ Mauvais compte Garmin pour le profil « {profil_path.name} ».\n"
            f"   Attendu : {attendu}\n"
            f"   Connecté : {compte} ({nom_affiche})\n"
            f"   Vérifie {profil_path / '.env'}, ou supprime le dossier de tokens "
            f"{TOKENSTORE_BASE / profil_path.name} pour te reconnecter."
        )

    if not attendu:
        if not memoriser:
            print(f"👤 Connecté : {nom_affiche} (non mémorisé : --dry-run)")
            return
        profile["garminDisplayName"] = compte
        profile_json.write_text(
            json.dumps(profile, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(f"🔗 Compte Garmin « {nom_affiche} » associé au profil « {profil_path.name} ».")
    else:
        print(f"👤 Connecté : {nom_affiche}")


def connexion(profil):
    try:
        from garminconnect import Garmin
    except ImportError:
        sys.exit("❌ garminconnect absent. Utilise le venv du projet :\n"
                 "   .venv/bin/python scripts/garmin_sync.py …\n"
                 "   (sinon : .venv/bin/pip install garminconnect)")

    charger_env(profil)
    email = os.getenv("GARMIN_EMAIL")
    mdp = os.getenv("GARMIN_PASSWORD")

    tokenstore = TOKENSTORE_BASE / profil
    tokenstore.mkdir(parents=True, exist_ok=True)

    api = Garmin(email=email, password=mdp,
                 prompt_mfa=lambda: input(f"Code MFA Garmin ({profil}) : ").strip())
    try:
        api.login(tokenstore=str(tokenstore))
    except Exception as e:
        if not email or not mdp:
            sys.exit(f"❌ Pas de session valide pour « {profil} » et GARMIN_EMAIL / "
                     f"GARMIN_PASSWORD introuvables.\n"
                     f"   Crée {PROFILES_DIR / profil / '.env'} "
                     f"(modèle : {ROOT / '.env.example'}).\n   Détail : {e}")
        sys.exit(f"❌ Connexion Garmin impossible pour « {profil} » : {e}")
    return api


# ---------------------------------------------------------------------------
# Détails enrichis
# ---------------------------------------------------------------------------

# Pause entre deux appels API. Garmin renvoie des 429 quand on enchaîne trop
# vite : le backfill fait 4 appels par activité, donc on lève le pied.
PAUSE_API = 0.4


def recuperer_details(api, act):
    """Données riches absentes de l'export CSV, best-effort par bloc."""
    aid = act.get("activityId")
    details = {"activityId": aid, "resume": act}
    blocs = {
        "splits": lambda: api.get_activity_splits(aid),
        "zonesFC": lambda: api.get_activity_hr_in_timezones(aid),
        "meteo": lambda: api.get_activity_weather(aid),
        "detail": lambda: api.get_activity(aid),
    }
    for nom, fn in blocs.items():
        try:
            details[nom] = fn()
        except Exception as e:
            details[nom] = {"erreur": str(e)}
        time.sleep(PAUSE_API)
    return details


def archiver_details(api, activites, raw_dir, forcer=False):
    """Écrit un JSON enrichi par activité. Saute celles déjà archivées."""
    raw_dir.mkdir(exist_ok=True)
    a_faire = [a for a in activites
               if forcer or not (raw_dir / f"{a.get('activityId')}.json").exists()]
    if not a_faire:
        return 0
    for i, a in enumerate(a_faire, 1):
        print(f"   [{i}/{len(a_faire)}] détails {f_date(a)} {a.get('activityName') or ''}", flush=True)
        cible = raw_dir / f"{a.get('activityId')}.json"
        cible.write_text(json.dumps(recuperer_details(api, a), ensure_ascii=False, indent=2),
                         encoding="utf-8")
    return len(a_faire)


# ---------------------------------------------------------------------------

def main():
    p = argparse.ArgumentParser(description="Synchronise les activités Garmin dans garmin.csv")
    p.add_argument("--profil", required=True)
    p.add_argument("--prepa", required=True)
    p.add_argument("--depuis", help="Date de début YYYY-MM-DD (défaut : lendemain de la dernière activité du CSV, sinon J-30)")
    p.add_argument("--jusqu-a", dest="jusqua", help="Date de fin YYYY-MM-DD (défaut : aujourd'hui)")
    p.add_argument("--details", action="store_true", help="Archive aussi le JSON enrichi (splits, zones FC, météo)")
    p.add_argument("--backfill", action="store_true",
                   help="Rattrape les détails manquants de TOUTE la prépa (implique --details ; "
                        "démarre à objectifs.dateDebutPrepa si --depuis est absent)")
    p.add_argument("--dry-run", action="store_true", help="Affiche ce qui serait ajouté sans rien écrire")
    args = p.parse_args()

    profil_dir = PROFILES_DIR / args.profil
    data_dir = profil_dir / "prepas" / args.prepa / "data"
    if not data_dir.parent.exists():
        sys.exit(f"❌ Prépa introuvable : {data_dir.parent}")
    data_dir.mkdir(parents=True, exist_ok=True)
    csv_path = data_dir / "garmin.csv"

    entetes, dates_connues, derniere = lire_existant(csv_path)

    if args.depuis:
        debut = args.depuis
    elif args.backfill:
        objectifs = {}
        try:
            objectifs = json.loads((data_dir / "objectifs.json").read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError):
            pass
        debut = objectifs.get("dateDebutPrepa") or (date.today() - timedelta(days=120)).isoformat()
    elif derniere:
        debut = (date.fromisoformat(derniere) + timedelta(days=1)).isoformat()
    else:
        debut = (date.today() - timedelta(days=30)).isoformat()
    fin = args.jusqua or date.today().isoformat()

    if debut > fin and not args.backfill:
        print(f"✅ Rien à faire : CSV déjà à jour jusqu'au {derniere}.")
        return

    print(f"🔄 {args.profil}/{args.prepa} — activités du {debut} au {fin}")
    api = connexion(args.profil)
    verifier_identite(api, profil_dir, memoriser=not args.dry_run)
    try:
        activites = api.get_activities_by_date(debut, fin)
    except Exception as e:
        sys.exit(f"❌ Récupération des activités impossible : {e}")

    activites.sort(key=lambda a: f_date(a) or "")
    nouvelles = [a for a in activites if (f_date(a) or "") not in dates_connues]
    doublons = len(activites) - len(nouvelles)

    if not nouvelles and not args.backfill:
        print(f"✅ Aucune nouvelle activité ({len(activites)} trouvée(s), déjà toutes dans le CSV).")
        return

    for a in nouvelles:
        dist = (a.get("distance") or 0) / 1000.0
        print(f"   + {f_date(a)}  {type_fr(a):<22} {dist:6.2f} km  {f_duree(a.get('duration'))}  {a.get('activityName') or ''}")
    if doublons:
        print(f"   ({doublons} déjà présente(s), ignorée(s))")

    if args.backfill:
        raw_dir = data_dir / "garmin_raw"
        manquants = [a for a in activites
                     if not (raw_dir / f"{a.get('activityId')}.json").exists()]
        print(f"   {len(activites)} activité(s) sur la période, "
              f"{len(manquants)} sans détails archivés.")

    if args.dry_run:
        print("\n🧪 --dry-run : rien n'a été écrit.")
        return

    if not nouvelles:
        print("   (CSV déjà complet sur la période)")
    nouveau_fichier = not csv_path.exists() or csv_path.stat().st_size == 0
    besoin_saut = not csv_a_saut_de_ligne_final(csv_path)
    with csv_path.open("a", encoding="utf-8", newline="") as f:
        if besoin_saut and not nouveau_fichier:
            f.write("\n")
        w = csv.writer(f, quoting=csv.QUOTE_MINIMAL, lineterminator="\n")
        if nouveau_fichier:
            w.writerow(entetes)
        for a in nouvelles:
            w.writerow(ligne_csv(a, entetes))
    if nouvelles:
        print(f"\n✅ {len(nouvelles)} activité(s) ajoutée(s) à {csv_path.relative_to(ROOT)}")

    if args.details or args.backfill:
        raw_dir = data_dir / "garmin_raw"
        cibles = activites if args.backfill else nouvelles
        n = archiver_details(api, cibles, raw_dir)
        print(f"✅ {n} détail(s) archivé(s) dans {raw_dir.relative_to(ROOT)}/")

    print(f"\n👉 Régénère la vue :\n   python3 scripts/build_data.py --profil {args.profil} --prepa {args.prepa}")


if __name__ == "__main__":
    main()
