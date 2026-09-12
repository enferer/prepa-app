#!/usr/bin/env python3
"""analyze.py — synthèse d'entraînement pour le coach (multi-profils).

Lit `profiles/<profil>/prepas/<prepa>/data/activites.json` et imprime un
bilan structuré : volume hebdo, allures réelles, meilleurs efforts, tendances FC.

stdlib uniquement. Lecture seule.

Deux niveaux de lecture, à ne pas mélanger :

- **global** (défaut) : tendances sur tout l'historique, depuis activites.json
  (issu de garmin.csv). C'est la vue macro — volume, allures, FC.
- **détaillé** (`--nouvelles`, `--seance`) : tours, zones FC et météo d'une
  séance précise, depuis detail_seances.json (issu de garmin_raw/). Réservé
  aux séances jamais analysées, pour ne pas noyer l'analyse macro.

Usage :
    python3 scripts/analyze.py --profil thibaut --prepa marathon-2026-10
    python3 scripts/analyze.py --profil thibaut --prepa marathon-2026-10 --jours 120
    python3 scripts/analyze.py --profil thibaut --prepa marathon-2026-10 --depuis 2026-04-01
    python3 scripts/analyze.py --profil thibaut --prepa marathon-2026-10 --nouvelles
    python3 scripts/analyze.py --profil thibaut --prepa marathon-2026-10 --seance 2026-09-11
    python3 scripts/analyze.py --profil thibaut --prepa marathon-2026-10 --marquer-analysees
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


# ---------------------------------------------------------------------------
# Niveau détaillé : une séance à la fois, depuis detail_seances.json
# ---------------------------------------------------------------------------

INTENSITES_FR = {
    "WARMUP": "échauffement",
    "ACTIVE": "actif",
    "REST": "récup",
    "RECOVERY": "récup",
    "COOLDOWN": "retour calme",
    "INTERVAL": "intervalle",
}


def charger_etat(data_dir):
    etat = charge(data_dir / "etat_analyse.json", {})
    return etat if isinstance(etat, dict) else {}


def seances_non_analysees(data_dir):
    """Fiches détaillées jamais passées en revue, de la plus ancienne à la plus récente."""
    fiches = charge(data_dir / "detail_seances.json", [])
    analysees = set((charger_etat(data_dir).get("analysees") or {}).keys())
    return [f for f in fiches if str(f.get("activityId")) not in analysees]


def marquer_analysees(data_dir, fiches, quand):
    etat = charger_etat(data_dir)
    etat.setdefault("analysees", {})
    for f in fiches:
        etat["analysees"][str(f.get("activityId"))] = {
            "le": quand, "date": f.get("date"), "titre": f.get("titre"),
        }
    (data_dir / "etat_analyse.json").write_text(
        json.dumps(etat, ensure_ascii=False, indent=2), encoding="utf-8")
    return len(fiches)


def afficher_fiche(f):
    """Rendu texte dense d'une séance : tout le signal, aucun remplissage."""
    duree = f.get("dureeSec") or 0
    print("=" * 66)
    print(f"{f.get('date')} · {f.get('titre') or '(sans titre)'}"
          f"   [{f.get('type') or '?'}{' · ' + f['lieu'] if f.get('lieu') else ''}]")
    print(f"  {f.get('distanceKm') or 0:.2f} km · {int(duree)//3600:d}h{(int(duree)%3600)//60:02d}:{int(duree)%60:02d}"
          f" · {fmt_pace(f.get('allureMoySecKm'))}/km"
          f"{' (GAP ' + fmt_pace(f.get('gapMoySecKm')) + ')' if f.get('gapMoySecKm') else ''}"
          f" · FC {f.get('fcMoy') or '--'} (max {f.get('fcMax') or '--'})"
          f" · D+{f.get('denivelePosM') or 0}")

    te = []
    if f.get("teAerobie") is not None:
        te.append(f"TE {f['teAerobie']} aéro / {f.get('teAnaerobie')} anaéro")
    if f.get("teLabel"):
        te.append(str(f["teLabel"]))
    if f.get("chargeEntrainement") is not None:
        te.append(f"charge {f['chargeEntrainement']}")
    if te:
        print("  " + " · ".join(te))

    m = f.get("meteo") or {}
    if m.get("temperatureC") is not None:
        print(f"  Météo : {m['temperatureC']}°C (ressenti {m.get('ressentiC')}°C)"
              f" · {m.get('humidite')}% humidité · vent {m.get('ventKmh')} km/h"
              f" · {m.get('description') or ''}")

    zones = [z for z in (f.get("zonesFC") or []) if (z.get("secondes") or 0) >= 30]
    if zones:
        print("  Zones FC : " + "  ".join(
            f"Z{z['zone']} {int(z['secondes'])//60}'" for z in zones))

    tours = f.get("tours") or []
    if tours:
        print(f"  --- {len(tours)} tours ---")
        for t in tours:
            intensite = INTENSITES_FR.get(t.get("intensite"), (t.get("intensite") or "").lower())
            d = t.get("dureeSec") or 0
            print(f"   {t['index']:>2} {intensite:<13} {t.get('distanceKm') or 0:5.2f} km"
                  f"  {int(d)//60:>2}:{int(d)%60:02d}"
                  f"  {fmt_pace(t.get('allureSecKm')):>5}/km"
                  f"  FC {t.get('fcMoy') or '--':>3}/{t.get('fcMax') or '--':<3}"
                  f"  cad {t.get('cadenceMoy') or '--':>3}"
                  f"  D+{t.get('denivelePosM') or 0:<3}")


def main():
    ap = argparse.ArgumentParser(description="Synthèse d'entraînement (multi-profils).")
    ap.add_argument("--profil", required=True, help="ID du profil (dossier profiles/<id>).")
    ap.add_argument("--prepa", required=True, help="ID de la prépa (dossier prepas/<id>).")
    ap.add_argument("--jours", type=int, default=90, help="Fenêtre 'récent' en jours (défaut 90).")
    ap.add_argument("--depuis", type=str, default=None, help="Date ISO de début (prime sur --jours).")
    ap.add_argument("--nouvelles", action="store_true",
                    help="Détail (tours, zones FC, météo) des séances jamais analysées, et rien d'autre.")
    ap.add_argument("--seance", type=str, default=None,
                    help="Détail d'une séance précise : date YYYY-MM-DD ou activityId.")
    ap.add_argument("--marquer-analysees", dest="marquer", nargs="?", const="toutes",
                    default=None, metavar="AVANT",
                    help="Marque les séances détaillées comme analysées (fin de /prepa-update). "
                         "Avec une date YYYY-MM-DD, ne marque que les séances antérieures — "
                         "utile pour initialiser l'état sur une prépa déjà suivie.")
    args = ap.parse_args()

    data_dir = resoudre_prepa(args.profil, args.prepa)

    # --- modes détaillés : exclusifs de la synthèse globale ---------------
    if args.seance:
        fiches = charge(data_dir / "detail_seances.json", [])
        cibles = [f for f in fiches
                  if f.get("date") == args.seance or str(f.get("activityId")) == args.seance]
        if not cibles:
            print(f"Aucune séance détaillée pour « {args.seance} ». "
                  f"Lance garmin_sync.py --backfill puis build_data.py.")
            return 1
        for f in cibles:
            afficher_fiche(f)
        return 0

    if args.nouvelles or args.marquer:
        nouvelles = seances_non_analysees(data_dir)
        if not nouvelles:
            print("✅ Aucune séance détaillée en attente d'analyse.")
            return 0
        if args.marquer:
            cibles = nouvelles
            if args.marquer != "toutes":
                cibles = [f for f in nouvelles if (f.get("date") or "") < args.marquer]
            if not cibles:
                print(f"Aucune séance à marquer (avant {args.marquer}).")
                return 0
            n = marquer_analysees(data_dir, cibles, datetime.now().strftime("%Y-%m-%d"))
            restantes = len(nouvelles) - n
            print(f"✅ {n} séance(s) marquée(s) comme analysées."
                  + (f" {restantes} reste(nt) à analyser." if restantes else ""))
            return 0
        print(f"### {len(nouvelles)} séance(s) jamais analysée(s) — niveau détaillé")
        print("(pour les tendances de fond, relance sans --nouvelles : "
              "la synthèse globale se lit sur tout l'historique)\n")
        for f in nouvelles:
            afficher_fiche(f)
        print("=" * 66)
        return 0
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
