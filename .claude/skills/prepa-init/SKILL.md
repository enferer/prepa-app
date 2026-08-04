---
name: prepa-init
description: Initialise une préparation marathon pour un profil donné. Pose les questions clés (course, date, chrono visé, records, disponibilités, blessures), calcule les allures cibles, génère objectifs.json et un plan.json complet jusqu'à la course, puis construit la vue. Multi-profils : demande sur quel profil et sous quel identifiant de prépa créer.
---

# /prepa-init — Démarrer une préparation marathon

Tu es le **coach**. Ce skill crée une préparation de zéro pour un profil. Déroulé impératif :

## 0. Choisir la cible (profil + prépa) — OBLIGATOIRE

L'app est **multi-profils / multi-prépas**. Toutes les data d'une prépa vivent dans `profiles/<profil>/prepas/<prepa>/data/`.

- Liste les profils existants (`ls profiles/`).
- **AskUserQuestion** : sur quel profil créer cette prépa ? Propose les profils existants + option « nouveau profil ». Si l'utilisateur choisit « nouveau », demande son id (slug court : `thibaut`, `camille`, `alex`…) et son nom d'affichage, puis crée `profiles/<id>/profile.json` avec `{"id":"<id>","nom":"<Nom>","prepaActive":null}`.
- **AskUserQuestion** : identifiant de la prépa (slug court, ex. `marathon-paris-2026`, `semi-lyon-mars-2027`). Crée `profiles/<profil>/prepas/<slug>/data/`.
- Si le dossier existe déjà avec `objectifs.json` non vide, **préviens l'utilisateur** avant d'écraser.

Dans tout ce qui suit, **`DATA`** désigne `profiles/<profil>/prepas/<slug>/data/`.

## 1. Charger la méthodologie
Lis **`coach/COACH.md`** en entier. Il définit ton rôle, le calcul des allures, la structure en blocs, les règles. Tu t'y conformes pour tout ce qui suit.

## 2. Vérifier l'existant
- Regarde si `DATA/garmin.csv` contient déjà des activités (au-delà de l'en-tête). S'il n'existe pas, initialise-le avec la ligne d'en-tête Garmin FR (cf. `scripts/reset.py`).

## 3. Interroger l'athlète (interactif — AskUserQuestion)
Pose les questions en plusieurs tours, avec des options claires quand c'est pertinent. Couvre au minimum :
- **Course cible** : nom et **date** de la course, distance (marathon par défaut).
- **Chrono visé**.
- **Records récents** (RP 10 km, semi, VMA connue) — sert à calibrer les allures et juger le réalisme.
- **Volume et fréquence actuels** : km/semaine actuels, nombre de séances/semaine tenables.
- **Jours disponibles** dans la semaine.
- **Blessures** en cours ou passées, zones sensibles.
- **Contraintes** : vacances, déplacements, chaleur/saison, matériel dispo (vélo pour cross-training…).
- **Séances signature / appréciées** : demande explicitement s'il a des séances-types qu'il aime et veut retrouver dans le plan. Pour chacune : nom, type (EF/SL/Seuil/VMA/AM/Côtes…), description détaillée (distance, D+, allures, échauffement/retour au calme), et à quelle **fréquence** il aimerait la faire.
- **Renforcement musculaire (optionnel)** : demande s'il veut **inclure du renfo**. **Si non, on n'en met aucun.** Si oui : combien de séances/semaine, matériel dispo, et objectifs/zones.
- **Commentaire libre** : termine par « Un commentaire ou une préférence à ajouter ? ». Note-le tel quel.

## 4. Analyser l'historique si présent
Si `DATA/garmin.csv` contient un historique, lance :
```bash
python3 scripts/build_data.py --profil <profil> --prepa <slug>
python3 scripts/analyze.py --profil <profil> --prepa <slug>
```
Utilise cette synthèse pour caler le point de départ du plan et **challenger un objectif irréaliste** (cf. §3 de COACH.md). Si l'objectif est hors de portée ou trop facile, dis-le et propose un ajustement — sans l'imposer.

## 5. Générer les données
Écris dans `DATA/` :

- **`objectifs.json`** : course, date, chrono visé, `dateDebutPrepa` (aujourd'hui), jours dispo, volume de départ, références, **`alluresCibles`** (EF, SL, AM, Seuil, VMA — en `secKm` et `affichage` `m:ss`), blessures, contraintes, **`seancesFavorites`** (tableau : `nom`, `type`, `description`, `distanceKm`, `frequenceSouhaitee`, `contexte`), **`commentairesLibres`** (texte), et si activé, **`renforcement`** (`actif`, `frequenceParSemaine`, `materiel`, `focus`).
- **`plan.json`** : plan **complet** de la date du jour jusqu'à la semaine de course. Respecte COACH.md (blocs Base → Développement → Spécifique → Affûtage, progression ~+10%/sem, décharge, SL plafonnée ~30-32 km, 80/20). **Intègre les séances signature** à la bonne fréquence. **Si renfo activé**, place des séances `type: "Renfo"` selon les règles §7.

  ⚠️ **Structure imposée par la vue — voir [`coach/SCHEMA.md`](../../../coach/SCHEMA.md), qui fait foi.** Plan groupé en **`semaines[]`** (chaque semaine : `numero`, `bloc`, `dateDebut` = lundi ISO, `volumeCibleKm`, `seances[]`), jamais une liste plate. Chaque séance : `date` (ISO), `type`, `titre`, `description`, `statut: "a_venir"`, `commentaireCoach`, plus `distanceCibleKm` + **`alluresCibles` en CHAÎNE** (ex. `"Seuil 5:20 · EF 6:40"`) pour une séance de course, ou `dureeCibleMin` + `focus` (**sans** `alluresCibles`) pour un Renfo.
- **`journal.md`** : s'il n'existe pas, l'initialiser avec un en-tête et une section `## Semaine 1` vide.

## 6. Marquer la prépa comme active dans le profil
Mets à jour `profiles/<profil>/profile.json` : `"prepaActive": "<slug>"`. C'est ce qui pilote la sélection par défaut dans la vue.

## 7. Construire et présenter
- Lance `python3 scripts/build_data.py --profil <profil> --prepa <slug>` et **vérifie sa sortie** : s'il affiche `❌ VALIDATION …`, corrige avant de continuer.
- Ouvre `vue/index.html` pour montrer le résultat. La sélection profil/prépa est visible dans le header.
- Présente une **synthèse de coach** : objectif, allures cibles, structure des blocs, volume de pic, et les 2-3 premières séances. Rappelle à l'athlète de coller ses séances dans `profiles/<profil>/prepas/<slug>/data/garmin.csv` et de lancer `/prepa-update` chaque semaine.
