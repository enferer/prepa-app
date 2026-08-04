#!/usr/bin/env python3
"""analyze.py — synthèse d'entraînement pour le coach (multi-profils).

Lit `profiles/<profil>/prepas/<prepa>/data/activites.json` et imprime un
bilan structuré : volume hebdo, allures réelles, meilleurs efforts, tendances FC.

stdlib uniquement. Lecture seule.

Usage :
    python3 scripts/analyze.py --profil thibaut --prepa marathon-2026-10
    python3 scripts/analyze.py --profil thibaut --prepa marathon-2026-10 --jours 120
    python3 scripts/analyze.py --profil thibaut --prepa marathon-2026-10 --depuis 2026-04-01
"""

import argparse
import json
import sys
from collections import defaultdict
from datetime import datetime, timedelta
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PROFILES_DIR = ROOT / "profiles"

TYPES_COURSE = {"Course à pied", "Trail", "Course sur tapis"}


def fmt_pace(sec):
    if not sec:
        return "  --  "
    m, s = divmod(int(round(sec)), 60)
    return f"{m}:{s:02d}"


def charge(path, defaut):
    if not path.exists():
        return defaut
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (json.JSONDecodeError, OSError):
        return defaut


def iso_semaine(dt):
    y, w, _ = dt.isocalendar()
    return (y, w)


def resoudre_prepa(profil, prepa):
    """Renvoie le dossier de la prépa ou lève."""
    pd = PROFILES_DIR / profil / "prepas" / prepa / "data"
    if not pd.exists():
        # Tentative d'auto-résolution si un seul profil / une seule prépa
        raise SystemExit(f"❌ Prépa introuvable : {pd.relative_to(ROOT)}")
    return pd


def main():
    ap = argparse.ArgumentParser(description="Synthèse d'entraînement (multi-profils).")
    ap.add_argument("--profil", required=True, help="ID du profil (dossier profiles/<id>).")
    ap.add_argument("--prepa", required=True, help="ID de la prépa (dossier prepas/<id>).")
    ap.add_argument("--jours", type=int, default=90, help="Fenêtre 'récent' en jours (défaut 90).")
    ap.add_argument("--depuis", type=str, default=None, help="Date ISO de début (prime sur --jours).")
    args = ap.parse_args()

    data_dir = resoudre_prepa(args.profil, args.prepa)
    acts = charge(data_dir / "activites.json", [])
    if not acts:
        print("Aucune activité. Lance d'abord scripts/build_data.py.")
        return 0

    runs = []
    for a in acts:
        if a.get("type") not in TYPES_COURSE:
            continue
        d = a.get("date")
        if not d:
            continue
        try:
            a = dict(a)
            a["_dt"] = datetime.fromisoformat(d)
            runs.append(a)
        except ValueError:
            continue
    runs.sort(key=lambda a: a["_dt"])
    if not runs:
        print("Aucune course datée exploitable.")
        return 0

    fin = runs[-1]["_dt"]
    if args.depuis:
        seuil = datetime.fromisoformat(args.depuis)
        label_fenetre = f"depuis le {args.depuis}"
    else:
        seuil = fin - timedelta(days=args.jours)
        label_fenetre = f"{args.jours} derniers jours"
    recent = [a for a in runs if a["_dt"] >= seuil]

    obj = charge(data_dir / "objectifs.json", {})
    allures = (obj.get("alluresCibles") or {})

    print("=" * 66)
    print(f"  SYNTHÈSE — {args.profil}/{args.prepa}")
    print("=" * 66)
    print(f"Période couverte : {runs[0]['date']} → {runs[-1]['date']}  ({len(runs)} courses)")
    if obj:
        c = obj.get("course", {})
        print(f"Objectif         : {c.get('nom','?')} le {c.get('date','?')} en {obj.get('chronoVise','?')}")
        if allures:
            zones = " · ".join(f"{k} {v.get('affichage','?')}" for k, v in allures.items())
            print(f"Allures cibles   : {zones}")

    vol = defaultdict(float)
    nbw = defaultdict(int)
    for a in runs:
        w = iso_semaine(a["_dt"])
        vol[w] += a.get("distanceKm") or 0
        nbw[w] += 1

    semaines_recentes = sorted({iso_semaine(a["_dt"]) for a in recent})
    print()
    print(f"--- Volume ({label_fenetre}) ---")
    if semaines_recentes:
        vols = [vol[w] for w in semaines_recentes]
        km_total = sum(a.get("distanceKm") or 0 for a in recent)
        print(f"  {len(recent)} courses · {km_total:.0f} km · {len(semaines_recentes)} semaines actives")
        print(f"  km/sem : moy {sum(vols)/len(vols):.1f} | min {min(vols):.1f} | max {max(vols):.1f}")
        print(f"  séances/sem : moy {sum(nbw[w] for w in semaines_recentes)/len(semaines_recentes):.1f}")

    print()
    print("--- 12 dernières semaines ISO ---")
    for w in sorted(vol.keys())[-12:]:
        barre = "█" * int(vol[w] / 3)
        print(f"  {w[0]}-S{w[1]:02d} : {vol[w]:5.1f} km ({nbw[w]}) {barre}")

    ef = [a for a in recent
          if (a.get("allureMoySecKm")) and (a.get("distanceKm") or 0) >= 5
          and (a.get("fcMoy") or 999) <= 145 and (a.get("ascensionM") or 0) < 120]
    if ef:
        moy = sum(a["allureMoySecKm"] for a in ef) / len(ef)
        print()
        print(f"--- Allure EF réelle (courses faciles récentes, n={len(ef)}) ---")
        print(f"  moyenne ~{fmt_pace(moy)}/km")

    print()
    print("--- Efforts rapides récents (≥8 km, D+<120 m, triés par allure) ---")
    rapides = sorted(
        [a for a in recent if (a.get("distanceKm") or 0) >= 8
         and (a.get("ascensionM") or 0) < 120 and a.get("allureMoySecKm")],
        key=lambda a: a["allureMoySecKm"])[:6]
    for a in rapides:
        print(f"  {a['date']} {a['distanceKm']:5.1f} km  {fmt_pace(a['allureMoySecKm'])}/km"
              f"  FC {a.get('fcMoy','--')}  D+{a.get('ascensionM','--')}  {(a.get('titre') or '')[:28]}")

    print()
    print("--- Plus longues sorties récentes ---")
    longues = sorted(recent, key=lambda a: -(a.get("distanceKm") or 0))[:5]
    for a in longues:
        print(f"  {a['date']} {a['distanceKm']:5.1f} km  {fmt_pace(a.get('allureMoySecKm'))}/km"
              f"  FC {a.get('fcMoy','--')}  D+{a.get('ascensionM','--')}  {(a.get('titre') or '')[:28]}")

    plus_longue = max(runs, key=lambda a: a.get("distanceKm") or 0)
    print(f"\n  Plus longue sortie (tout l'historique) : "
          f"{plus_longue['distanceKm']:.1f} km le {plus_longue['date']}")
    print("=" * 66)
    return 0


if __name__ == "__main__":
    sys.exit(main())
