#!/usr/bin/env python3
"""reset.py — remet une prépa à zéro (multi-profils).

Deux niveaux :
  --plan (défaut) : supprime objectifs.json + plan.json + activites.json, réinit journal.md.
                    GARDE data/garmin.csv (historique).
  --full          : en plus, vide data/garmin.csv (garde seulement l'en-tête).

Usage :
  python3 scripts/reset.py --profil thibaut --prepa marathon-2026-10
  python3 scripts/reset.py --profil thibaut --prepa marathon-2026-10 --full
  python3 scripts/reset.py --profil thibaut --prepa marathon-2026-10 --full --yes
"""

import argparse
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PROFILES_DIR = ROOT / "profiles"

JOURNAL_ENTETE = """# Journal de prépa

> Écris librement, une section par semaine (`## Semaine N`). Note ici tout ce qui compte : humeur, sommeil, blessures, douleurs, météo, motivation, contexte de vie. Le coach lit ce journal pour adapter le plan.
"""


def entete_csv(csv_path):
    defaut = (
        "Type d'activité,Date,Favori,Titre,Distance,Calories,Durée,"
        "Fréquence cardiaque moyenne,Fréquence cardiaque maximale,TE aérobie,"
        "Cadence de course moyenne,Cadence de course maximale,Allure moyenne,"
        "Meilleure allure,Ascension totale,Descente totale,Longueur moyenne des foulées,"
        "Rapport vertical moyen,Oscillation verticale moyenne,Temps de contact moyen avec le sol,"
        "GAP moyenne,Normalized Power® (NP®),Training Stress Score® (TSS®),Puissance moyenne,"
        "Puissance max.,Pas,Consommation du Body Battery,Décompression,Temps du meilleur circuit,"
        "Nombre de tours,Stress moyen,Stress maximal,Temps de déplacement,Temps écoulé,"
        "Altitude minimale,Altitude maximale"
    )
    if csv_path.exists():
        try:
            first = csv_path.read_text(encoding="utf-8-sig").splitlines()
            if first and first[0].strip():
                return first[0]
        except OSError:
            pass
    return defaut


def rm(path):
    if path.exists():
        path.unlink()
        print(f"🗑️  supprimé   {path.relative_to(ROOT)}")


def main():
    ap = argparse.ArgumentParser(description="Reset d'une prépa (multi-profils).")
    ap.add_argument("--profil", required=True)
    ap.add_argument("--prepa", required=True)
    ap.add_argument("--full", action="store_true", help="vide aussi garmin.csv")
    ap.add_argument("--yes", action="store_true", help="pas de confirmation")
    args = ap.parse_args()

    data = PROFILES_DIR / args.profil / "prepas" / args.prepa / "data"
    if not data.exists():
        print(f"❌ Prépa introuvable : {data.relative_to(ROOT)}", file=sys.stderr)
        return 1

    objectifs = data / "objectifs.json"
    plan = data / "plan.json"
    activites = data / "activites.json"
    garmin = data / "garmin.csv"
    journal = data / "journal.md"

    print(f"Réinitialisation {args.profil}/{args.prepa} —",
          "TOTALE (--full)" if args.full else "plan seulement")
    print("  • supprime : objectifs.json, plan.json, activites.json")
    print("  • réinit   : journal.md")
    if args.full:
        print("  • vide     : garmin.csv (en-tête conservé)")
    else:
        print("  • garde    : garmin.csv")

    if not args.yes:
        rep = input("\nConfirmer ? Tape 'oui' : ").strip().lower()
        if rep not in ("oui", "o", "yes", "y"):
            print("Annulé.")
            return 1

    rm(objectifs)
    rm(plan)
    rm(activites)

    journal.write_text(JOURNAL_ENTETE, encoding="utf-8")
    print(f"↺  réinitialisé {journal.relative_to(ROOT)}")

    if args.full:
        garmin.write_text(entete_csv(garmin) + "\n", encoding="utf-8")
        print(f"↺  vidé        {garmin.relative_to(ROOT)}")

    print("\n✅ Reset terminé. Étapes suivantes :")
    print(f"   1. python3 scripts/build_data.py --profil {args.profil} --prepa {args.prepa}")
    print("   2. lance /prepa-init pour repartir")
    return 0


if __name__ == "__main__":
    sys.exit(main())
