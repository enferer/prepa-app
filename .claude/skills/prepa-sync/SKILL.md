---
name: prepa-sync
description: Synchronise les activités Garmin d'une prépa sans rien analyser ni adapter. Demande quel profil synchroniser — un seul ou tous —, lance garmin_sync.py, régénère la vue et liste simplement les séances récupérées. Utilise ce skill pour « récupérer / synchroniser mes activités » ; pour un bilan de semaine et une adaptation du plan, c'est /prepa-update.
---

# /prepa-sync — Récupérer les activités Garmin

Skill **purement technique** : il rapatrie les séances de Garmin Connect et régénère la vue. Il **n'analyse rien, ne juge rien, n'adapte rien**.

## Périmètre — RÈGLE IMPORTANTE

Ce que ce skill fait :
- récupérer les nouvelles activités dans `DATA/garmin.csv` (+ détails dans `DATA/garmin_raw/`) ;
- régénérer `activites.json`, `detail_seances.json` et `vue/data.js` ;
- lister factuellement ce qui a été ajouté.

Ce que ce skill **ne fait pas**, même si l'occasion semble bonne :
- pas de `analyze.py` (ni global, ni `--nouvelles`) ;
- pas de `--marquer-analysees` — les séances récupérées doivent rester « neuves » pour que `/prepa-update` les analyse ;
- pas de modification de `plan.json` (aucun statut, aucun `commentaireCoach`), ni de `journal.md`, ni d'`objectifs.json` ;
- pas de commentaire de coach sur l'exécution des séances.

Si l'athlète pose une question d'entraînement pendant le sync, réponds brièvement et **oriente-le vers `/prepa-update`** pour le bilan complet.

## 1. Choisir la cible — OBLIGATOIRE

- Liste les profils (`ls profiles/`) et, pour chaque profil, ses prépas (`ls profiles/<id>/prepas/`) et sa `prepaActive` (`profiles/<id>/profile.json`).
- **AskUserQuestion** : quel profil synchroniser ? Propose **un choix par profil existant, plus une option « Tous les profils »** qui les enchaîne tous.
- **Un seul profil** : si sa `prepaActive` est non nulle et existe, propose-la par défaut ; s'il a plusieurs prépas, **AskUserQuestion** pour choisir laquelle.
- **Tous les profils** : synchronise la `prepaActive` de chacun, sans reposer de question. Si un profil n'a pas de `prepaActive` valide (absente, ou dossier inexistant) et qu'il a exactement une prépa, prends-la ; sinon **saute ce profil** et signale-le dans le rapport final plutôt que d'interrompre la série.
- Si la cible est donnée directement en argument du skill (ex. `/prepa-sync thibaut marathon-2026-10`, ou `/prepa-sync tous`), ne repose pas la question.

Dans la suite, **`DATA`** = `profiles/<profil>/prepas/<slug>/data/`. Le §2 s'exécute **une fois par cible retenue** ; le §3 une seule fois, à la fin.

## 2. Synchroniser

```bash
.venv/bin/python scripts/garmin_sync.py --profil <profil> --prepa <slug> --details
```

- Le script ne récupère que les activités postérieures à la dernière ligne du CSV, dédoublonne par date+heure et est relançable sans risque.
- `--details` archive le dump brut de chaque nouvelle séance dans `DATA/garmin_raw/` : c'est lui qui alimentera plus tard l'analyse détaillée. **Toujours le passer.**
- Le compte utilisé est celui de `profiles/<profil>/.env` ; le script s'arrête si le compte connecté ne correspond pas au profil ciblé.

Variantes utiles selon la demande :

| Besoin | Commande |
|---|---|
| Aperçu sans écrire | `… --dry-run` |
| Rattraper une période ancienne | `… --depuis 2026-09-01` (option `--jusqu-a`) |
| Récupérer les détails manquants sur des séances déjà en CSV | `… --backfill` |

Si le sync échoue (identifiants absents, MFA, panne Garmin, compte Garmin ne correspondant pas au profil), **dis-le clairement** avec le message d'erreur et ne bricole pas le CSV à la main. Sur plusieurs profils, **passe au suivant** et regroupe les échecs dans le rapport final : l'échec d'un athlète ne doit pas empêcher la synchro des autres.

## 3. Régénérer la vue

```bash
python3 scripts/build_data.py --profil <profil> --prepa <slug>   # une cible
python3 scripts/build_data.py                                    # tout, après un sync « tous les profils »
```

**Vérifie sa sortie** : s'il affiche `❌ VALIDATION …`, signale-le. Ce type d'erreur vient d'un `plan.json`/`objectifs.json` non conforme, pas du sync — ne le corrige pas ici, mentionne-le et renvoie vers `/prepa-update`.

## 4. Rapporter

Résumé **court et factuel**, sans jugement. Sur plusieurs profils, une section par profil (`Thibaut — marathon-2026-10 : 3 activités`), puis une ligne de total :
- nombre d'activités ajoutées (ou « aucune nouvelle activité ») ;
- pour chacune : date, type, distance, durée, allure moyenne, FC moyenne — tels quels, depuis la sortie du script ou `DATA/activites.json` ;
- état de la vue (régénérée, ou erreur de validation) ;
- **les profils sautés ou en échec**, avec la raison — ne les passe jamais sous silence.

Termine en rappelant que ces séances **ne sont pas encore analysées** et que `/prepa-update` s'en chargera.

Propose un **commit git** pour tracer la récupération, par exemple :
`git add -A && git commit -m "Sync Garmin <profil>/<slug>"` (ou `"Sync Garmin tous profils"`)
