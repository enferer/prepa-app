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
| `garmin.csv` | `garmin_sync.py` ou **l'utilisateur** | Séances réellement effectuées, au format de l'export Garmin. Synchronisé automatiquement, complétable à la main. |
| `journal.md` | **L'utilisateur** | Journal par semaine (`## Semaine N`) : humeur, blessures, météo, ressenti. |
| `objectifs.json` | Skill `/prepa-init` | Course, date, chrono visé, allures cibles, références, contraintes. |
| `plan.json` | Skills | Plan complet : semaines → séances, avec statuts. |
| `activites.json` | **Généré** | Version normalisée de `garmin.csv`. Ne jamais éditer à la main. |
| `garmin_raw/<id>.json` | `garmin_sync.py --details` | Dump brut d'une séance (~40 Ko) : splits, zones FC, météo. **Ne jamais lire directement.** |
| `detail_seances.json` | **Généré** | Extrait utile des dumps (~3 Ko/séance). C'est *cette* source qu'on lit. |
| `etat_analyse.json` | `analyze.py --marquer-analysees` | Séances déjà passées en revue, pour ne pas les réanalyser. |

Seuls `garmin.csv` et `journal.md` sont éditables à la main — et `garmin.csv` est normalement rempli par `garmin_sync.py`. Tout le reste est géré par les skills.

Avec `--details`, `garmin_sync.py` archive aussi `data/garmin_raw/<activityId>.json` : splits par km, temps dans chaque zone FC, training effect aérobie **et** anaérobie, météo, VO2max. Ces données n'existent pas dans l'export CSV et ne sont pas encore exploitées par la vue.

## Règle d'or : régénérer la vue

**Après toute modification d'un fichier `data/`**, relance :

```bash
python3 scripts/build_data.py                              # tout rebuilder
python3 scripts/build_data.py --profil thibaut             # 1 profil
python3 scripts/build_data.py --profil thibaut --prepa marathon-2026-10   # 1 prépa
```

Le script scanne `profiles/*/prepas/*/data/`, parse chaque `garmin.csv` → `activites.json`, valide `plan.json`/`objectifs.json` contre `coach/SCHEMA.md`, et écrit un **catalogue complet** dans `vue/data.js` (`window.PREPA_DATA = { profils: [{ id, nom, prepaActive, prepas: [...] }] }`). Il affiche `❌ VALIDATION …` en cas d'écart de contrat — **toujours vérifier sa sortie**.

## Synchronisation Garmin

`scripts/garmin_sync.py` récupère les activités depuis Garmin Connect et les ajoute à `garmin.csv`, au format exact de l'export officiel (il réutilise les en-têtes du fichier existant). Plus besoin de copier les séances à la main.

```bash
.venv/bin/python scripts/garmin_sync.py --profil thibaut --prepa marathon-2026-10            # depuis la dernière activité connue
.venv/bin/python scripts/garmin_sync.py --profil thibaut --prepa marathon-2026-10 --dry-run  # aperçu, n'écrit rien
.venv/bin/python scripts/garmin_sync.py --profil thibaut --prepa marathon-2026-10 --depuis 2026-09-01 --details
.venv/bin/python scripts/garmin_sync.py --profil camille --prepa marathon-2026-10            # autre athlète, autre compte Garmin
```

- **Dépendances** : ce script est le seul à sortir de la stdlib (`garminconnect`). Il tourne dans `.venv/` (Python 3.12) — les autres scripts restent en `python3` système.
- **Identifiants, un jeu par profil** : `GARMIN_EMAIL` / `GARMIN_PASSWORD` dans **`profiles/<profil>/.env`** — chaque athlète a son compte Garmin. Un `.env` à la racine sert de repli pour les profils sans fichier dédié (voir `.env.example`) ; tous sont gitignorés.
- **Sessions isolées** : les tokens OAuth sont cachés dans `~/.garminconnect/<profil>` (~1 an), un dossier par athlète — deux profils ne s'écrasent donc jamais.
- **Garde-fou d'identité** : au premier sync réussi, le compte Garmin connecté est mémorisé dans `profile.json` (`garminDisplayName`). Aux suivants, un compte qui ne correspond pas **arrête** le sync au lieu d'écrire les activités d'un athlète dans le CSV d'un autre.
- **Idempotent** : dédoublonnage par date+heure, relançable sans créer de doublons.
- Après sync, **toujours** relancer `build_data.py` (règle d'or ci-dessus).

## Détail des séances : deux niveaux, à ne pas confondre

Les dumps `garmin_raw/` sont volumineux et bruyants. `scripts/details.py` en extrait une **fiche compacte** par séance (tours, zones FC, météo, training effect), matérialisée dans `detail_seances.json` et injectée dans la vue. **Rien ne lit le brut, ni la vue ni le coach.**

| Niveau | Source | Commande | Usage |
|---|---|---|---|
| Global | `activites.json` | `analyze.py …` | Tendances de fond (volume, allures, FC) sur tout l'historique. |
| Détaillé | `detail_seances.json` | `analyze.py … --nouvelles` | Exécution d'une séance : tours, dérive FC, récups. Séances neuves uniquement. |

```bash
python3 scripts/analyze.py --profil thibaut --prepa marathon-2026-10 --nouvelles          # séances jamais analysées
python3 scripts/analyze.py --profil thibaut --prepa marathon-2026-10 --seance 2026-09-11  # une séance précise
python3 scripts/analyze.py --profil thibaut --prepa marathon-2026-10 --marquer-analysees  # clôture (fin de /prepa-update)
```

## Contrat de données & analyse

- **[`coach/SCHEMA.md`](coach/SCHEMA.md)** — forme exacte d'`objectifs.json` et `plan.json` attendue par la vue. **Fait foi**.
- **`scripts/analyze.py --profil <id> --prepa <slug>`** — synthèse en lecture seule depuis `activites.json` (volume hebdo, allures réelles, meilleurs efforts, tendances FC). À lancer dans `/prepa-init` et `/prepa-update`.
- **`scripts/reset.py --profil <id> --prepa <slug>`** — reset d'une prépa (option `--full` pour vider aussi `garmin.csv`).

## La vue (`vue/`)

Page web autonome : ouvrir `vue/index.html` par double-clic. Dans le header, deux dropdowns (**Profil** / **Prépa**) permettent de basculer entre les prépas ; la sélection est persistée en `localStorage`. Cinq onglets : Tableau de bord, **Séances**, Stats, Journal, Profil.

L'onglet **Séances** navigue sortie par sortie (liste chronologique + fiche détaillée : graphe d'allure par tour, zones de FC, météo), alimenté par `detail_seances.json`. Flèches ◀ ▶ ou touches gauche/droite pour passer d'une séance à l'autre.

Le tableau se lit en deux modes, via la bascule **Blocs / Tours** :
- **Blocs** (défaut) — les tours consécutifs de même intensité sont regroupés : les 3×2 km d'un seuil apparaissent en 3 lignes, avec allure et FC agrégées (FC pondérée par la durée) et le détail km par km en colonne. C'est la lecture utile pour juger l'exécution d'une séance à blocs.
- **Tours** — le découpage brut de la montre.

La bascule n'apparaît que sur les séances **structurées** (dont les tours mélangent plusieurs intensités). Sur une sortie en auto-lap, tout serait fondu en un bloc unique : la vue reste donc km par km.

## Skills

- `/prepa-init` — démarre une nouvelle prépa pour un profil. Demande d'abord le profil (existant ou nouveau) et le slug de la prépa, puis le questionnaire habituel.
- `/prepa-update` — MàJ hebdo d'une prépa. Demande d'abord le profil + la prépa cible, puis rapproche activités et séances prévues et adapte la suite.

Les skills prennent toujours `--profil <id> --prepa <slug>` en argument des commandes Python.
