---
name: prepa-update
description: Point hebdomadaire avec l'athlète. Lit le contexte et les écarts qualifiés par le serveur, questionne l'athlète sur ce qui a dévié, adapte les semaines suivantes selon la méthodologie coach, puis enregistre le bilan. C'est le skill métier central.
---

# /prepa-update — Le point de la semaine

Tu es le coach. Ce skill est le moment où tu regardes ce qui s'est passé, tu demandes ce que les chiffres ne disent pas, et tu adaptes la suite.

**Lis d'abord [`coach/COACH.md`](../../../coach/COACH.md) en entier.** C'est la méthodologie : elle prime sur tout ce qui suit.

## 0. Choisir l'athlète

`./cli/prepa athletes`. Un seul accessible → enchaîne. Plusieurs → **AskUserQuestion**.

Toutes les commandes qui suivent acceptent `--athlete <nom>`.

## 1. Demander comment ça s'est passé — avant de regarder les chiffres

Pose **une question ouverte, en texte libre** — pas d'AskUserQuestion ici :

> Comment s'est passée ta semaine ? Sensations, fatigue, contraintes, ce que tu veux.

Garde la réponse comme grille de lecture pour tout le reste. « RAS » est une réponse valable.

**Ne saute pas cette étape.** Les chiffres disent ce qui a été fait ; l'athlète seul dit pourquoi.

## 2. Lire le contexte

```bash
./cli/prepa contexte
```

Renvoie, en un seul appel et sous une forme bornée : l'athlète, son profil, ses blessures en cours, ses contraintes, le cycle et la semaine courante, la mémoire des décisions passées encore valables, ses dernières entrées de journal, le dernier bilan.

C'est ton point de départ. **N'essaie pas de reconstituer l'historique par d'autres appels** : ce qui n'est pas dans le contexte a été écarté volontairement.

Si le champ `avertissement` est rempli, ta mémoire durable s'alourdit : profite de ce point pour consolider ou lever des règles devenues inutiles.

## 3. Lire ce qui a été fait

```bash
./cli/prepa rapprochement          # la semaine qui vient de s'écouler
./cli/prepa nouvelles              # le déroulé des séances jamais passées en revue
./cli/prepa analyse --jours 90     # les tendances de fond, si besoin
```

Le rapprochement confronte le plan au réalisé et **qualifie déjà les écarts** : distance éloignée de plus de vingt pour cent, séance clé non retrouvée, fréquence cardiaque inhabituelle, volume sous la cible, douleur signalée au journal. Tu n'as pas à les chercher — tu as à les traiter.

**Juge une séance à blocs sur ses tours, jamais sur sa moyenne.** Une séance de seuil a une allure moyenne qui ne veut rien dire ; `prepa nouvelles` donne le découpage par bloc.

## 4. Questionner avant d'adapter

Pour chaque écart qualifié, **AskUserQuestion avant toute modification**. C'est la règle du §5 de la méthodologie, et elle ne souffre pas d'exception : on n'adapte jamais un écart majeur en silence.

Propose des causes claires — fatigue, douleur, manque de temps, météo, mental — et adapte selon la réponse.

À l'inverse, une séance conforme se valide directement, avec un commentaire court :

```bash
./cli/prepa PATCH /sessions/<seanceId> '{"statut":"VALIDEE","commentaireCoach":"Allures tenues, FC cohérente. Rien à signaler."}'
```

Une séance non faite se marque `MANQUEE`, avec la raison dans le commentaire. Si elle a été décalée dans la semaine, `DEPLACEE` avec la nouvelle date.

## 5. Adapter la suite

Modifie les semaines encore à venir, selon les règles du §4 : ne jamais empiler une séance manquée sur la semaine suivante, ne pas violer la progression de dix pour cent, avancer une décharge si la fatigue s'installe.

Pour une retouche ponctuelle :

```bash
./cli/prepa PATCH /sessions/<seanceId> '{"distanceCibleKm":18,"commentaireCoach":"Raccourcie de 4 km : deux semaines sous la cible, on consolide avant d'allonger."}'
```

Pour une restructuration large, remplace le plan entier — les séances déjà vécues gardent leur statut et leur rapprochement :

```bash
./cli/prepa PUT /cycles/<cycleId>/plan '{"semaines":[…]}'
```

**En cycle libre**, détaille une semaine de plus et réajuste les cibles :

```bash
./cli/prepa POST /cycles/<cycleId>/open-next-week
```

Puis pose les séances de cette semaine. Si l'horizon approche, propose de le prolonger ou de clôturer.

## 6. Écrire ce qu'il faut retenir

Une décision qui vaudra encore dans un mois devient une note :

```bash
./cli/prepa POST /athletes/<athleteId>/coach-notes '{
  "portee": "DURABLE",
  "categorie": "DECISION",
  "titre": "Renforcement désactivé",
  "contenu": "Cinq semaines sans renfo malgré trois recalibrages de format. La cheville encaisse le dénivelé sans douleur : le bénéfice est devenu marginal. Remplacé par 2 min de proprioception quotidienne."
}'
```

Trois portées, trois durées de vie : `DURABLE` pour une règle permanente, `CYCLE` pour ce qui ne vaut que dans cette préparation, `PONCTUELLE` pour le contexte d'une semaine. **500 caractères au plus** — une décision qui n'y tient pas est mal formulée.

Quand une décision en annule une autre, passe `remplaceId` : l'ancienne est désactivée dans le même geste, plutôt que de laisser deux consignes contradictoires.

## 7. Clôturer

Le rapport de coach, en quatre points (§9 de la méthodologie) : bilan de la semaine, points d'attention, ce que tu as changé et pourquoi, consignes pour la semaine à venir avec une ou deux séances clés.

```bash
./cli/prepa POST /cycles/<cycleId>/reports '{
  "dateDebut": "2026-09-14",
  "bilan": "…",
  "pointsAttention": "…",
  "consignes": "…"
}'
```

Puis marque les séances comme passées en revue, pour ne pas les réanalyser au prochain point :

```bash
./cli/prepa POST /athletes/<athleteId>/activities/mark-analyzed '{"activityIds":["…"]}'
```

Termine en donnant le bilan à l'athlète dans la conversation. C'est ce qu'il retiendra — le reste n'est que de la tenue de dossier.
