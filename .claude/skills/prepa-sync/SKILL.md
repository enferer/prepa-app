---
name: prepa-sync
description: Déclenche la synchronisation Garmin d'un athlète et liste les séances récupérées. N'analyse rien et n'adapte rien. Utilise ce skill pour « récupérer / synchroniser mes activités » ; pour un bilan de semaine et une adaptation du plan, c'est /prepa-update.
---

# /prepa-sync — Récupérer les activités Garmin

Skill **purement technique**. Il demande au serveur d'aller chercher les nouvelles séances et rapporte ce qui est arrivé. Il **n'analyse rien, ne juge rien, n'adapte rien**.

Le sync tourne normalement tout seul, chaque nuit, sur le serveur. Ce skill sert à le forcer quand on ne veut pas attendre — après une sortie qu'on vient de terminer, par exemple.

## Périmètre — règle importante

Ce que ce skill fait :
- déclencher une synchronisation pour un athlète ou pour tous ;
- attendre son résultat et lister factuellement les séances récupérées.

Ce que ce skill **ne fait pas**, même si l'occasion semble bonne :
- aucun appel à `prepa analyse`, `prepa rapprochement` ou `prepa nouvelles` ;
- aucun marquage des séances comme analysées — elles doivent rester **neuves** pour que `/prepa-update` les passe en revue ;
- aucune écriture sur le plan, le journal ou le profil ;
- aucun commentaire de coach sur l'exécution des séances.

Si l'athlète pose une question d'entraînement pendant le sync, réponds brièvement et **oriente-le vers `/prepa-update`** pour le bilan complet.

## 1. Choisir la cible

`./cli/prepa athletes` liste les athlètes accessibles.

- Un seul athlète accessible : enchaîne directement.
- Plusieurs : **AskUserQuestion** avec une option par athlète, plus une option « Tous ».
- Si l'athlète est nommé dans la demande (`/prepa-sync camille`), n'interroge pas.

## 2. Déclencher la synchronisation

```bash
./cli/prepa POST /athletes/<athleteId>/sync
```

Puis attends et relis l'état :

```bash
./cli/prepa GET /athletes/<athleteId>/sync-status
```

Le worker relève les demandes toutes les quelques minutes. Si le statut est encore `EN_ATTENTE` après deux relectures espacées d'une minute, dis-le simplement : la synchronisation est en file, les séances arriveront. **N'attends pas indéfiniment.**

En cas d'échec sur un athlète, passe au suivant et regroupe les erreurs à la fin. Une erreur d'authentification Garmin veut dire que les identifiants doivent être remis à jour côté serveur — dis-le sans tenter de les deviner.

## 3. Rapporter

Un rapport court et factuel, une section par athlète :

- nombre de séances récupérées, avec date, type et distance ;
- ce qui a échoué, le cas échéant ;
- rien d'autre. **Pas de jugement sur les séances**, pas de « belle sortie ».

Termine par un rappel : *rien n'a été analysé ; lance `/prepa-update` pour le bilan de la semaine.*
