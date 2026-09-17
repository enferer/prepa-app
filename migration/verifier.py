#!/usr/bin/env python3
"""Verification de la migration : compare la base aux fichiers d'origine.

Usage : python3 migration/verifier.py /chemin/vers/profiles

Un ecart n'est acceptable que s'il est explicable — les seuls attendus ici sont
les corrections de lecture des exports Garmin. Tout le reste doit tomber juste.
"""

import json
import subprocess
import sys
from pathlib import Path

RACINE = Path(__file__).resolve().parent.parent

# Dossier `profiles/` de l'ancienne application, passe en argument.
PROFILES = None


def sql(requete):
    sortie = subprocess.run(
        ["docker", "compose", "exec", "-T", "postgres", "psql", "-U", "prepa", "-d", "prepa",
         "-tA", "-F", "|", "-c", requete],
        capture_output=True, text=True, cwd=RACINE)
    return [l.split("|") for l in sortie.stdout.strip().split("\n") if l]


def verifier(profil):
    profile_json = json.loads((PROFILES / profil / "profile.json").read_text(encoding="utf-8"))
    nom = profile_json.get("nom", profil)
    slug = profile_json.get("prepaActive")
    data = PROFILES / profil / "prepas" / slug / "data"

    activites = json.loads((data / "activites.json").read_text(encoding="utf-8"))
    plan = json.loads((data / "plan.json").read_text(encoding="utf-8"))

    attendu = {
        "activites": len(activites),
        "km": round(sum(a["distanceKm"] or 0 for a in activites), 2),
        "semaines": len(plan["semaines"]),
        "seances": sum(len(s["seances"]) for s in plan["semaines"]),
        "validees": sum(1 for s in plan["semaines"] for x in s["seances"] if x["statut"] == "validee"),
        "manquees": sum(1 for s in plan["semaines"] for x in s["seances"] if x["statut"] == "manquee"),
        "volumeCible": round(sum(s["volumeCibleKm"] for s in plan["semaines"]), 1),
    }

    ligne = sql(f"""
        select
          (select count(*) from activities a join athletes t on t.id = a.athlete_id
             where t.display_name = '{nom}'),
          (select round(sum(coalesce(distance_m,0))/1000.0, 2) from activities a
             join athletes t on t.id = a.athlete_id where t.display_name = '{nom}'),
          (select count(*) from training_weeks w join cycles c on c.id = w.cycle_id
             join athletes t on t.id = c.athlete_id where t.display_name = '{nom}'),
          (select count(*) from planned_sessions s join cycles c on c.id = s.cycle_id
             join athletes t on t.id = c.athlete_id where t.display_name = '{nom}'),
          (select count(*) from planned_sessions s join cycles c on c.id = s.cycle_id
             join athletes t on t.id = c.athlete_id where t.display_name = '{nom}' and s.statut = 'VALIDEE'),
          (select count(*) from planned_sessions s join cycles c on c.id = s.cycle_id
             join athletes t on t.id = c.athlete_id where t.display_name = '{nom}' and s.statut = 'MANQUEE'),
          (select round(sum(w.volume_cible_km), 1) from training_weeks w join cycles c on c.id = w.cycle_id
             join athletes t on t.id = c.athlete_id where t.display_name = '{nom}')
    """)[0]

    obtenu = {
        "activites": int(ligne[0]), "km": float(ligne[1]), "semaines": int(ligne[2]),
        "seances": int(ligne[3]), "validees": int(ligne[4]), "manquees": int(ligne[5]),
        "volumeCible": float(ligne[6]),
    }

    print(f"\n== {nom} ==")
    ecarts = 0
    for cle, valeur in attendu.items():
        reel = obtenu[cle]
        ok = abs(reel - valeur) < 0.01 if isinstance(valeur, float) else reel == valeur
        print(f"  {'OK  ' if ok else 'ECART'} {cle:12} attendu {valeur:>10} | base {reel:>10}")
        ecarts += 0 if ok else 1
    return ecarts


def main():
    if len(sys.argv) < 2:
        print("Usage : python3 migration/verifier.py /chemin/vers/profiles", file=sys.stderr)
        return 2

    global PROFILES
    PROFILES = Path(sys.argv[1]).expanduser().resolve()
    if not PROFILES.is_dir():
        print(f"Dossier source introuvable : {PROFILES}", file=sys.stderr)
        return 2

    profils = sorted(p.name for p in PROFILES.iterdir() if (p / "profile.json").exists())
    total = sum(verifier(p) for p in profils)
    print(f"\n{'Migration conforme.' if total == 0 else f'{total} ecart(s) a expliquer.'}")
    return 0 if total == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
