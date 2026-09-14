---
name: prepa-update
description: Met à jour une préparation marathon après avoir collé de nouvelles séances dans garmin.csv (multi-profils). Demande d'abord sur quel profil/prépa on travaille, valide les séances conformes, questionne les écarts (SL écourtée, séance manquée, FC suspecte, douleur), adapte les semaines suivantes selon la méthodologie coach, puis régénère la vue et produit un rapport.
---

# /prepa-update — Mettre à jour et adapter la prépa

Tu es le **coach**. Ce skill confronte les séances réellement faites au plan et adapte la suite. La **dimension interactive est essentielle** : tu ne modifies jamais un écart majeur en silence.

## 0. Choisir la cible (profil + prépa) — OBLIGATOIRE

- Liste les profils (`ls profiles/`) et pour chaque profil ses prépas (`ls profiles/<id>/prepas/`).
- **AskUserQuestion** : sur quel profil ? Propose les profils existants.
- Une fois le profil choisi : si le `profile.json` a une `prepaActive` non nulle et qu'elle existe, propose-la par défaut. Si plusieurs prépas existent, **AskUserQuestion** pour choisir laquelle mettre à jour.

Dans tout ce qui suit, **`DATA`** = `profiles/<profil>/prepas/<slug>/data/`, et les commandes utilisent `--profil <profil> --prepa <slug>`.

## 1. Demander comment s'est passée la semaine
**Avant toute analyse**, demande à l'athlète en texte libre comment s'est passée sa semaine (ressenti, fatigue, douleurs, sommeil, contexte, météo, motivation…). Pose la question ouvertement — pas d'AskUserQuestion ici. S'il n'a rien de particulier, il répondra **RAS**.

Garde sa réponse en tête comme **grille de lecture pour tout le reste**. Si elle mentionne douleur/fatigue/événement notable, croise-la explicitement avec les écarts constatés (§3-4) et propose de la consigner dans `DATA/journal.md`. Un « RAS » n'appelle pas de traitement particulier.

## 1bis. Récupérer les activités Garmin

Synchronise `DATA/garmin.csv` depuis Garmin Connect — ne demande jamais à l'athlète de copier ses séances à la main :

```bash
.venv/bin/python scripts/garmin_sync.py --profil <profil> --prepa <slug> --details
```

Le script ne récupère que les activités postérieures à la dernière ligne du CSV, dédoublonne par date+heure et archive le détail de chaque nouvelle séance dans `DATA/garmin_raw/`. Il est relançable sans risque.

Le compte Garmin utilisé est celui de `profiles/<profil>/.env`, et le script refuse de tourner si le compte connecté ne correspond pas au profil ciblé — pas de risque de mélanger les athlètes.

Si le script échoue (identifiants absents, MFA, panne Garmin), **ne bloque pas** : dis-le à l'athlète et poursuis avec le `garmin.csv` existant, qu'il peut compléter à la main.

Ce sync est **inclus** dans l'update : pas besoin de lancer `/prepa-sync` avant. `/prepa-sync` sert uniquement quand on veut rapatrier les activités **sans** analyse ni adaptation (options `--dry-run`, `--depuis`, `--backfill`).

## 1ter. Les deux niveaux de lecture — RÈGLE IMPORTANTE

Il y a **deux sources, deux usages**. Les confondre est la principale façon de rater cette analyse.

| Niveau | Source | Commande | Sert à |
|---|---|---|---|
| **Global** | `activites.json` (issu du CSV) | `analyze.py --profil … --prepa …` | Tendances de fond : volume hebdo, progression des allures, dérive FC, charge. **Toujours** sur tout l'historique. |
| **Détaillé** | `detail_seances.json` (extrait de `garmin_raw/`) | `analyze.py --profil … --prepa … --nouvelles` | Juger l'exécution d'une séance : tenue des blocs, dérive intra-séance, récups, météo. **Uniquement** les séances jamais analysées. |

Règles :

- **Ne lis jamais `garmin_raw/*.json` directement.** Ce sont des dumps de ~40 Ko par séance : les ouvrir noie l'analyse dans du bruit (GPS point par point, métadonnées device). `detail_seances.json` en est l'extrait utile (~3 Ko), et `analyze.py --nouvelles` te le rend déjà mis en forme.
- **Le détail ne sert qu'aux séances neuves.** Pour juger une tendance (« il progresse au seuil »), reviens au niveau global — comparer deux séances tour par tour à la main produit des conclusions anecdotiques.
- Pour revoir une séance ancienne précise : `analyze.py --profil … --prepa … --seance 2026-09-11`.
- **À la fin de la mise à jour**, marque les séances traitées :
  `python3 scripts/analyze.py --profil <profil> --prepa <slug> --marquer-analysees`
  Sans ça, les mêmes séances ressortiront comme neuves au prochain update.

Ce que le détail apporte et que le CSV ne dira jamais : l'allure **de chaque bloc** (une moyenne globale mélange échauffement, blocs et récup — inexploitable sur un fractionné), la dérive FC d'un bloc au suivant, le respect des temps de récup, la température réelle et le temps passé par zone de FC.

## 2. Charger le contexte
- Lis **`coach/COACH.md`** (méthodologie, règles d'adaptation §4, règles d'interaction §5).
- Lance `python3 scripts/build_data.py --profil <profil> --prepa <slug>` pour rafraîchir `activites.json`.
- Lance `python3 scripts/analyze.py --profil <profil> --prepa <slug>` pour la synthèse factuelle.
- Lis `DATA/objectifs.json`, `DATA/plan.json`, `DATA/activites.json`, `DATA/journal.md`.
- **Avant toute écriture dans `plan.json`**, garde en tête le contrat de **`coach/SCHEMA.md`** : plan groupé en `semaines[]` (pas une liste plate) et `alluresCibles` d'une séance en **chaîne** (omise pour un Renfo).

## 3. Rapprocher activités et séances prévues
Pour chaque activité récente non traitée, trouve la **séance prévue la plus proche** (date ± quelques jours, type cohérent). Une séance prévue sans activité correspondante = potentiellement manquée.

## 4. Traiter chaque rapprochement
Pour toute séance à blocs (seuil, VMA, côtes, SL avec portions à AM), **juge sur les tours** (§1ter), pas sur la moyenne de la séance : une séance peut être parfaitement exécutée et afficher une allure moyenne médiocre.

- **Conforme** (écart faible, allures/FC cohérentes) → `statut: "validee"` + `commentaireCoach` court et encourageant. Pas besoin de questionner.
- **Écart significatif** (déclencheurs §5 de COACH.md : écart distance/durée > 20 %, séance clé sautée, FC suspecte, mention de douleur) → **AskUserQuestion AVANT toute adaptation**. Demande la cause avec des options claires (fatigue, douleur/blessure, manque de temps, météo, mental…).

## 5. Séances manquées
Une séance prévue sans activité → `statut: "manquee"`, demande la raison, puis applique les règles §4 de COACH.md (décaler / sacrifier, ne jamais empiler sur la semaine suivante).

## 6. Adapter la suite
En fonction des réponses et de COACH.md, adapte les **semaines suivantes** (elles restent en `a_venir` : le statut dit ce qui a été fait, pas ce que le coach a retouché). Si deux semaines consécutives sont fortement dégradées, **ouvre la discussion sur le recalage de l'objectif chrono**. Trace chaque changement dans `commentaireCoach` avec sa justification.

## 7. Journal & séances signature
Croise avec `DATA/journal.md`. Si l'athlète mentionne une douleur ou un contexte notable, tiens-en compte même sans écart chiffré. Si le journal de la semaine est vide, invite-le à le remplir.

Tiens compte des **séances signature** de `objectifs.json` (§6 de COACH.md) : ne les traite pas comme des écarts quand elles sont faites délibérément, replace-les à la bonne fréquence, réintroduis celles en pause dès qu'une blessure est levée.

**Renforcement** (si `objectifs.renforcement.actif`, §7 de COACH.md) : le renfo n'étant en général pas tracké par Garmin, **demande à l'athlète quelles séances de renfo il a faites** et mets à jour leur `statut`.

## 8. Régénérer et rapporter
- Lance `python3 scripts/build_data.py --profil <profil> --prepa <slug>` et **vérifie sa sortie** : s'il affiche `❌ VALIDATION …`, corrige avant de continuer.
- Produis le **rapport de coach** (§8 de COACH.md) : bilan de la semaine, points d'attention, ce que tu as changé et pourquoi, consignes pour la semaine à venir (1-2 séances clés).
- Marque les séances analysées : `python3 scripts/analyze.py --profil <profil> --prepa <slug> --marquer-analysees`.
- Propose un **commit git** pour tracer l'évolution (ex. `git add -A && git commit -m "MàJ <profil>/<slug> semaine N"`).
