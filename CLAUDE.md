# prepa-app

Système de préparation avec dimension coach, **multi-profils** et **multi-prépas**. Une seule app, un seul jeu de scripts et une seule vue web, capable de gérer plusieurs athlètes et plusieurs prépas par athlète.

## Structure

```
prepa-app/
├── coach/                       COACH.md (méthodologie), SCHEMA.md (contrat data)
├── scripts/                     build_data.py, analyze.py, reset.py
├── vue/                         page web statique (data.js généré)
├── .claude/skills/              prepa-init, prepa-update
└── profiles/
    ├── <profil>/
    │   ├── profile.json         { id, nom, prepaActive }
    │   └── prepas/
    │       └── <prepa-slug>/
    │           └── data/        garmin.csv, journal.md, objectifs.json, plan.json, activites.json
    └── …
```

- Un **profil** = un athlète (dossier `profiles/<id>/`).
- Une **prépa** = une préparation ciblée (dossier `profiles/<id>/prepas/<slug>/`). Un profil peut avoir plusieurs prépas (par exemple `marathon-paris-2026` puis `semi-lyon-mars-2027`).
- Le champ `prepaActive` du `profile.json` désigne la prépa affichée par défaut dans la vue quand ce profil est sélectionné.

## Rôle coach — IMPORTANT

Pour **toute question d'entraînement, d'allure, d'adaptation ou de comportement de coach**, tu DOIS d'abord lire [`coach/COACH.md`](coach/COACH.md). C'est le cerveau du projet : méthodologie, calcul des allures, règles d'adaptation, règles d'interaction.

## Fichiers data (dans chaque prépa)

| Fichier | Qui l'édite | Rôle |
|---|---|---|
| `garmin.csv` | **L'utilisateur** | Export Garmin (format FR), séances réellement effectuées. |
| `journal.md` | **L'utilisateur** | Journal par semaine (`## Semaine N`) : humeur, blessures, météo, ressenti. |
| `objectifs.json` | Skill `/prepa-init` | Course, date, chrono visé, allures cibles, références, contraintes. |
| `plan.json` | Skills | Plan complet : semaines → séances, avec statuts. |
| `activites.json` | **Généré** | Version normalisée de `garmin.csv`. Ne jamais éditer à la main. |

Seuls `garmin.csv` et `journal.md` sont édités à la main. Tout le reste est géré par les skills.

## Règle d'or : régénérer la vue

**Après toute modification d'un fichier `data/`**, relance :

```bash
python3 scripts/build_data.py                              # tout rebuilder
python3 scripts/build_data.py --profil thibaut             # 1 profil
python3 scripts/build_data.py --profil thibaut --prepa marathon-2026-10   # 1 prépa
```

Le script scanne `profiles/*/prepas/*/data/`, parse chaque `garmin.csv` → `activites.json`, valide `plan.json`/`objectifs.json` contre `coach/SCHEMA.md`, et écrit un **catalogue complet** dans `vue/data.js` (`window.PREPA_DATA = { profils: [{ id, nom, prepaActive, prepas: [...] }] }`). Il affiche `❌ VALIDATION …` en cas d'écart de contrat — **toujours vérifier sa sortie**.

## Contrat de données & analyse

- **[`coach/SCHEMA.md`](coach/SCHEMA.md)** — forme exacte d'`objectifs.json` et `plan.json` attendue par la vue. **Fait foi**.
- **`scripts/analyze.py --profil <id> --prepa <slug>`** — synthèse en lecture seule depuis `activites.json` (volume hebdo, allures réelles, meilleurs efforts, tendances FC). À lancer dans `/prepa-init` et `/prepa-update`.
- **`scripts/reset.py --profil <id> --prepa <slug>`** — reset d'une prépa (option `--full` pour vider aussi `garmin.csv`).

## La vue (`vue/`)

Page web autonome : ouvrir `vue/index.html` par double-clic. Dans le header, deux dropdowns (**Profil** / **Prépa**) permettent de basculer entre les prépas ; la sélection est persistée en `localStorage`. Quatre onglets : Dashboard, Plan, Stats, Journal.

## Skills

- `/prepa-init` — démarre une nouvelle prépa pour un profil. Demande d'abord le profil (existant ou nouveau) et le slug de la prépa, puis le questionnaire habituel.
- `/prepa-update` — MàJ hebdo d'une prépa. Demande d'abord le profil + la prépa cible, puis rapproche activités et séances prévues et adapte la suite.

Les skills prennent toujours `--profil <id> --prepa <slug>` en argument des commandes Python.
