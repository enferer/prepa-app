#!/usr/bin/env python3
"""build_data.py — pipeline data multi-profils / multi-prépas.

Scanne `profiles/<profil>/prepas/<prepa>/data/` pour chaque prépa, parse
`garmin.csv` -> `activites.json`, valide `objectifs.json` + `plan.json`,
puis assemble un catalogue complet dans `vue/data.js` (`window.PREPA_DATA`).

stdlib uniquement. Idempotent et déterministe.

Usage :
    python3 scripts/build_data.py                    # tout rebuilder
    python3 scripts/build_data.py --profil thibaut   # 1 profil (toutes ses prépas)
    python3 scripts/build_data.py --profil thibaut --prepa marathon-2026-10
"""

import argparse
import csv
import json
import re
import sys
from datetime import datetime
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PROFILES_DIR = ROOT / "profiles"
VUE = ROOT / "vue"
DATA_JS = VUE / "data.js"


# ---------------------------------------------------------------------------
# Helpers de parsing Garmin
# ---------------------------------------------------------------------------

def _clean(val):
    if val is None:
        return None
    v = val.strip()
    if v in ("", "--", "...", "—"):
        return None
    return v


def to_float(val):
    v = _clean(val)
    if v is None:
        return None
    v = v.replace(" ", "").replace(" ", "").replace(" ", "")
    if "," in v and "." in v:
        if v.rfind(",") > v.rfind("."):
            v = v.replace(".", "").replace(",", ".")
        else:
            v = v.replace(",", "")
    else:
        v = v.replace(",", ".")
    try:
        return float(v)
    except ValueError:
        return None


def to_int(val):
    f = to_float(val)
    return int(round(f)) if f is not None else None


def duration_to_seconds(val):
    v = _clean(val)
    if v is None:
        return None
    parts = v.split(":")
    try:
        parts = [float(p.replace(",", ".")) for p in parts]
    except ValueError:
        return None
    if len(parts) == 3:
        h, m, s = parts
    elif len(parts) == 2:
        h, m, s = 0.0, parts[0], parts[1]
    elif len(parts) == 1:
        h, m, s = 0.0, 0.0, parts[0]
    else:
        return None
    return h * 3600 + m * 60 + s


def pace_to_seconds(val):
    v = _clean(val)
    if v is None:
        return None
    m = re.search(r"(\d+):(\d{1,2})", v)
    if not m:
        return None
    return int(m.group(1)) * 60 + int(m.group(2))


def parse_date(val):
    v = _clean(val)
    if v is None:
        return None, None
    fmts = [
        "%Y-%m-%d %H:%M:%S", "%Y-%m-%d %H:%M", "%Y-%m-%d",
        "%d/%m/%Y %H:%M:%S", "%d/%m/%Y %H:%M", "%d/%m/%Y",
    ]
    for fmt in fmts:
        try:
            dt = datetime.strptime(v, fmt)
            return dt.strftime("%Y-%m-%d"), dt.isoformat()
        except ValueError:
            continue
    m = re.match(r"(\d{4})-(\d{2})-(\d{2})", v)
    if m:
        return m.group(0), None
    m = re.match(r"(\d{2})/(\d{2})/(\d{4})", v)
    if m:
        return f"{m.group(3)}-{m.group(2)}-{m.group(1)}", None
    return None, None


COLONNES = {
    "type": ("Type d'activité", _clean),
    "favori": ("Favori", lambda v: (_clean(v) or "").lower() in ("oui", "true", "1", "yes")),
    "titre": ("Titre", _clean),
    "distanceKm": ("Distance", to_float),
    "calories": ("Calories", to_int),
    "dureeSec": ("Durée", duration_to_seconds),
    "fcMoy": ("Fréquence cardiaque moyenne", to_int),
    "fcMax": ("Fréquence cardiaque maximale", to_int),
    "teAerobie": ("TE aérobie", to_float),
    "cadenceMoy": ("Cadence de course moyenne", to_int),
    "cadenceMax": ("Cadence de course maximale", to_int),
    "allureMoySecKm": ("Allure moyenne", pace_to_seconds),
    "meilleureAllureSecKm": ("Meilleure allure", pace_to_seconds),
    "ascensionM": ("Ascension totale", to_int),
    "descenteM": ("Descente totale", to_int),
    "longueurFouleeM": ("Longueur moyenne des foulées", to_float),
    "oscillationVerticale": ("Oscillation verticale moyenne", to_float),
    "tempsContactSol": ("Temps de contact moyen avec le sol", to_int),
    "gapMoySecKm": ("GAP moyenne", pace_to_seconds),
    "np": ("Normalized Power® (NP®)", to_int),
    "tss": ("Training Stress Score® (TSS®)", to_float),
    "puissanceMoy": ("Puissance moyenne", to_int),
    "puissanceMax": ("Puissance max.", to_int),
    "pas": ("Pas", to_int),
    "nbTours": ("Nombre de tours", to_int),
    "tempsDeplacementSec": ("Temps de déplacement", duration_to_seconds),
    "tempsEcouleSec": ("Temps écoulé", duration_to_seconds),
    "altitudeMinM": ("Altitude minimale", to_int),
    "altitudeMaxM": ("Altitude maximale", to_int),
}


def parse_garmin(csv_path):
    if not csv_path.exists():
        return []
    activites = []
    with csv_path.open(encoding="utf-8-sig", newline="") as f:
        reader = csv.DictReader(f)
        if reader.fieldnames is None:
            return []
        headers = {h.strip(): h for h in reader.fieldnames if h}
        for row in reader:
            if not any((v or "").strip() for v in row.values()):
                continue
            act = {}
            for champ, (entete, conv) in COLONNES.items():
                brut = row.get(headers.get(entete, entete))
                act[champ] = conv(brut)
            date_iso, dt_iso = parse_date(row.get(headers.get("Date", "Date")))
            act["date"] = date_iso
            act["dateHeure"] = dt_iso
            activites.append(act)
    activites.sort(key=lambda a: a["date"] or "9999-12-31")
    return activites


def parse_journal(journal_path):
    if not journal_path.exists():
        return []
    texte = journal_path.read_text(encoding="utf-8")
    entrees = []
    blocs = re.split(r"^##\s+", texte, flags=re.MULTILINE)
    for bloc in blocs:
        bloc = bloc.strip()
        if not bloc:
            continue
        lignes = bloc.split("\n", 1)
        titre = lignes[0].strip()
        contenu = lignes[1].strip() if len(lignes) > 1 else ""
        m = re.search(r"[Ss]emaine\s+(\d+)", titre)
        semaine = int(m.group(1)) if m else None
        entrees.append({"semaine": semaine, "titre": titre, "contenu": contenu})
    return entrees


def load_json(path, defaut):
    if not path.exists():
        return defaut
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (json.JSONDecodeError, OSError) as e:
        print(f"⚠️  {path} illisible ({e}), valeur par défaut utilisée.", file=sys.stderr)
        return defaut


# ---------------------------------------------------------------------------
# Validation du contrat de données (identique v1)
# ---------------------------------------------------------------------------
_STATUTS_CONNUS = {"a_venir", "validee", "adaptee", "modifiee", "manquee"}
_ISO_DATE = re.compile(r"^\d{4}-\d{2}-\d{2}$")


def valider_plan(plan):
    err = []
    if not isinstance(plan, dict):
        return ["plan.json : la racine doit être un objet JSON."]
    if "semaines" not in plan:
        if "seances" in plan:
            err.append("plan.json : liste plate `seances` détectée mais la vue attend "
                       "`semaines[]`. Voir coach/SCHEMA.md.")
        else:
            err.append("plan.json : clé `semaines` absente.")
        return err
    sems = plan.get("semaines")
    if not isinstance(sems, list):
        return ["plan.json : `semaines` doit être un tableau."]
    for i, s in enumerate(sems):
        ref = f"semaines[{i}]"
        if not isinstance(s, dict):
            err.append(f"{ref} : doit être un objet.")
            continue
        if not isinstance(s.get("numero"), int):
            err.append(f"{ref} : `numero` (entier) manquant.")
        if not (isinstance(s.get("dateDebut"), str) and _ISO_DATE.match(s.get("dateDebut", ""))):
            err.append(f"{ref} : `dateDebut` doit être une date ISO 'YYYY-MM-DD'.")
        if not isinstance(s.get("volumeCibleKm"), (int, float)):
            err.append(f"{ref} : `volumeCibleKm` (nombre) manquant.")
        seances = s.get("seances")
        if not isinstance(seances, list):
            err.append(f"{ref} : `seances` doit être un tableau.")
            continue
        for j, se in enumerate(seances):
            sref = f"{ref}.seances[{j}]"
            if not isinstance(se, dict):
                err.append(f"{sref} : doit être un objet.")
                continue
            if "alluresCibles" in se and not isinstance(se["alluresCibles"], str):
                err.append(f"{sref} ({se.get('titre','?')}) : `alluresCibles` doit être une CHAÎNE.")
            if not isinstance(se.get("date"), str) or not _ISO_DATE.match(se.get("date", "")):
                err.append(f"{sref} : `date` doit être une date ISO 'YYYY-MM-DD'.")
            if se.get("statut") not in _STATUTS_CONNUS:
                err.append(f"{sref} : `statut` inconnu ({se.get('statut')!r}). "
                           f"Attendu l'un de {sorted(_STATUTS_CONNUS)}.")
            if se.get("type") == "Renfo" and not se.get("focus"):
                err.append(f"{sref} : une séance Renfo devrait porter un `focus`.")
    return err


def valider_objectifs(obj):
    err = []
    if not isinstance(obj, dict) or not obj:
        return []
    course = obj.get("course")
    if not isinstance(course, dict) or not course.get("date"):
        err.append("objectifs.json : `course.date` manquante.")
    ac = obj.get("alluresCibles")
    if ac is not None and not isinstance(ac, dict):
        err.append("objectifs.json : `alluresCibles` doit être un objet {zone: {secKm, affichage}}.")
    return err


# ---------------------------------------------------------------------------
# Scan multi-profils
# ---------------------------------------------------------------------------

def build_prepa(prepa_dir):
    """Traite un dossier prépa : parse garmin.csv, valide, retourne le payload."""
    data_dir = prepa_dir / "data"
    garmin = data_dir / "garmin.csv"
    activites_json = data_dir / "activites.json"
    objectifs_json = data_dir / "objectifs.json"
    plan_json = data_dir / "plan.json"
    journal_md = data_dir / "journal.md"

    activites = parse_garmin(garmin)
    activites_json.write_text(json.dumps(activites, ensure_ascii=False, indent=2), encoding="utf-8")

    objectifs = load_json(objectifs_json, {})
    plan = load_json(plan_json, {"semaines": []})

    erreurs = valider_objectifs(objectifs) + valider_plan(plan)

    meta = load_json(prepa_dir / "prepa.json", {})

    return {
        "id": prepa_dir.name,
        "nom": meta.get("nom") or (objectifs.get("course") or {}).get("nom") or prepa_dir.name,
        "objectifs": objectifs,
        "plan": plan,
        "activites": activites,
        "journal": parse_journal(journal_md),
    }, erreurs, len(activites)


def build_profil(profil_dir, filtre_prepa=None):
    profile = load_json(profil_dir / "profile.json", {"id": profil_dir.name, "nom": profil_dir.name.title()})
    prepas_dir = profil_dir / "prepas"
    prepas = []
    erreurs_totales = []
    if prepas_dir.exists():
        for pd in sorted(prepas_dir.iterdir()):
            if not pd.is_dir():
                continue
            if filtre_prepa and pd.name != filtre_prepa:
                # on charge quand même les données existantes (déjà générées) pour ne rien perdre du catalogue
                data_dir = pd / "data"
                prepas.append({
                    "id": pd.name,
                    "nom": (load_json(data_dir / "objectifs.json", {}).get("course") or {}).get("nom") or pd.name,
                    "objectifs": load_json(data_dir / "objectifs.json", {}),
                    "plan": load_json(data_dir / "plan.json", {"semaines": []}),
                    "activites": load_json(data_dir / "activites.json", []),
                    "journal": parse_journal(data_dir / "journal.md"),
                })
                continue
            payload, err, nb = build_prepa(pd)
            prepas.append(payload)
            if err:
                erreurs_totales.append((profile["id"], pd.name, err))
            print(f"  ✅ {profile['id']}/{pd.name} : {nb} activité(s)")

    return {
        "id": profile.get("id", profil_dir.name),
        "nom": profile.get("nom", profil_dir.name.title()),
        "prepaActive": profile.get("prepaActive"),
        "prepas": prepas,
    }, erreurs_totales


def main():
    ap = argparse.ArgumentParser(description="Build multi-profils.")
    ap.add_argument("--profil", help="Ne rebuild que ce profil (les autres restent tels quels dans le catalogue).")
    ap.add_argument("--prepa", help="Ne rebuild que cette prépa du profil ciblé.")
    args = ap.parse_args()

    if not PROFILES_DIR.exists():
        print(f"❌ Dossier {PROFILES_DIR} inexistant.", file=sys.stderr)
        return 1

    profils = []
    erreurs = []
    for pd in sorted(PROFILES_DIR.iterdir()):
        if not pd.is_dir():
            continue
        # Si un profil est ciblé, on ne rebuild QUE ses prépas ; les autres profils sont pris en l'état.
        if args.profil and pd.name != args.profil:
            prof, _ = build_profil(pd, filtre_prepa="__none__")  # ne rebuild aucune prépa, catalogue seulement
            profils.append(prof)
            continue
        prof, err = build_profil(pd, filtre_prepa=args.prepa)
        profils.append(prof)
        erreurs.extend(err)

    if erreurs:
        print("\n" + "=" * 64, file=sys.stderr)
        print("❌ VALIDATION : certaines prépas ne respectent pas le contrat.", file=sys.stderr)
        for pid, sid, errs in erreurs:
            print(f"  [{pid}/{sid}]", file=sys.stderr)
            for e in errs:
                print(f"     • {e}", file=sys.stderr)
        print("=" * 64 + "\n", file=sys.stderr)

    payload = {
        "genereLe": datetime.now().isoformat(timespec="seconds"),
        "profils": profils,
    }

    VUE.mkdir(parents=True, exist_ok=True)
    contenu = (
        "// Fichier GÉNÉRÉ par scripts/build_data.py — NE PAS ÉDITER À LA MAIN.\n"
        "// Relancer `python3 scripts/build_data.py` après toute modif dans profiles/*/prepas/*/data/.\n"
        "window.PREPA_DATA = "
        + json.dumps(payload, ensure_ascii=False, indent=2)
        + ";\n"
    )
    DATA_JS.write_text(contenu, encoding="utf-8")

    total_prepas = sum(len(p["prepas"]) for p in profils)
    print(f"\n✅ Catalogue généré : {len(profils)} profil(s), {total_prepas} prépa(s) -> {DATA_JS.relative_to(ROOT)}")
    if erreurs:
        print(f"⚠️  {len(erreurs)} prépa(s) avec erreur(s) de validation (voir plus haut).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
