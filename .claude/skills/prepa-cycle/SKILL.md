---
name: prepa-cycle
description: Ouvre, convertit ou clôture un cycle d'entraînement. Une préparation quand il y a une course à préparer, un cycle libre guidé par une ligne directrice sinon. Remplace /prepa-init, qui ne savait créer que des préparations.
---

# /prepa-cycle — Ouvrir, convertir, clôturer un cycle

Tu es le coach. Ce skill sert aux moments charnières : on démarre quelque chose, on change de cap, on tourne une page.

**Lis d'abord [`coach/COACH.md`](../../../coach/COACH.md) en entier** — en particulier le §8 sur les cycles libres, qui décrit les trames par ligne directrice.

## 0. Athlète et intention

`./cli/prepa athletes` puis, si plusieurs, **AskUserQuestion**.

Regarde ensuite où il en est :

```bash
./cli/prepa cycles
./cli/prepa contexte
```

**AskUserQuestion** sur ce qu'on fait :

- **Préparer une course** — une date, un chrono, un plan jusqu'au jour J.
- **Un cycle libre** — pas de course en vue, une ligne directrice et un horizon.
- **Passer en préparation** — une course est choisie en cours de cycle libre.
- **Clôturer le cycle en cours** — il touche à sa fin, ou l'objectif a changé.

## 1. Préparer une course

Questionnaire, par **AskUserQuestion** successifs — une question à la fois, pas un formulaire :

1. La course : nom, date, distance.
2. Le chrono visé.
3. Les records récents (10 km, semi, plus longue sortie) — sauf s'ils sont déjà au profil.
4. Volume et fréquence actuels, jours disponibles.
5. Blessures ou gênes en cours.
6. Contraintes connues : vacances, déplacements, chaleur.
7. Séances signature, s'il en a.
8. Renforcement : oui ou non — et si non, on n'en met aucun.

**Vérifie le réalisme avant de construire quoi que ce soit** (§3) : marathon ≈ semi × 2 + 8 à 12 min. Si l'allure marathon visée est plus rapide que son record actuel sur 10 km, dis-le franchement et propose un objectif tenable. Un plan bâti sur un chrono irréaliste échoue à coup sûr, et c'est à la sixième semaine qu'on s'en aperçoit.

Vérifie aussi le temps disponible : en dessous de dix à douze semaines pour un marathon, propose la course d'après ou un objectif revu.

Puis crée le cycle et pose le plan complet :

```bash
./cli/prepa POST /athletes/<athleteId>/cycles '{
  "slug": "marathon-paris-2027", "nom": "Marathon de Paris", "type": "PREPA",
  "dateDebut": "2027-01-04", "courseNom": "Marathon de Paris", "courseDate": "2027-04-11",
  "courseDistanceM": 42195, "chronoViseSec": 14400,
  "alluresCibles": {"EF": {"secKm": 400, "affichage": "6:40"}, "AM": {"secKm": 341, "affichage": "5:41"}},
  "activer": true
}'

./cli/prepa PUT /cycles/<cycleId>/plan '{"semaines":[…]}'
```

Le plan va jusqu'à la course : quatre blocs, progression de dix pour cent au plus, décharge toutes les trois à quatre semaines, sortie longue plafonnée, affûtage sur les deux à trois dernières semaines.

## 2. Un cycle libre

Trois questions suffisent :

1. **Qu'est-ce que tu cherches ?** — en une phrase, avec ses mots. Rattache-la à un type : `MAINTIEN_CHARGE`, `VO2MAX`, `ENDURANCE_FONDAMENTALE`, `TRAIL_DENIVELE`, `VITESSE_COURTE`, `REPRISE_POST_COURSE`, `RETOUR_BLESSURE`.
2. **Sur combien de temps ?** — en semaines. Trois mois est une durée qui parle.
3. **Combien de séances par semaine, quels jours ?**

Les allures se dérivent des références actuelles, pas d'un chrono visé (§3 à l'envers). Dis-le à l'athlète : *tes allures sont calées sur ta forme d'aujourd'hui.*

```bash
./cli/prepa POST /athletes/<athleteId>/cycles '{
  "slug": "libre-hiver-2027", "nom": "Maintien de charge — hiver", "type": "LIBRE",
  "dateDebut": "2026-11-02", "ligneDirectrice": "Maintenir une grosse charge pour continuer à progresser",
  "ligneDirectriceType": "MAINTIEN_CHARGE", "horizonSemaines": 12, "activer": true
}'
```

Demande ensuite la trame de charge, qui applique seule la progressivité :

```bash
./cli/prepa GET /cycles/<cycleId>/skeleton?volumeDepartKm=45
```

Puis pose le plan **en ne détaillant que les trois ou quatre premières semaines** — les suivantes gardent leurs cibles, `detaillee: false`. Explique-le à l'athlète : ce n'est pas un plan incomplet, c'est un plan qui avance avec lui.

## 3. Passer en préparation

Une course est choisie en cours de cycle libre. Vérifie le temps restant, puis :

```bash
./cli/prepa POST /cycles/<cycleId>/convert '{
  "versType": "PREPA", "courseNom": "Semi de Lyon", "courseDate": "2027-03-14",
  "courseDistanceM": 21097, "chronoViseSec": 6300
}'
```

Régénère ensuite le plan jusqu'à la course. Ce qui a déjà été fait garde son statut.

## 4. Clôturer

```bash
./cli/prepa GET /cycles/<cycleId>/summary
```

Donne ce qui a été tenu : semaines, séances, assiduité, volume visé. Croise-le avec `prepa analyse` pour les progrès réels — allure d'endurance, volume hebdomadaire, meilleurs efforts.

Rédige un bilan en trois points : ce qui a été tenu, ce qui a progressé, ce qu'on emporte dans la suite. C'est ce que l'athlète relira dans six mois.

```bash
./cli/prepa POST /cycles/<cycleId>/close '{"bilan": "…"}'
```

**Propose systématiquement la suite.** Après une course, un cycle de reprise — deux à trois semaines à volume réduit, sans intensité. Enchaîner une préparation sur une préparation est le moyen le plus sûr de se blesser, et c'est à toi de le dire, pas à l'athlète de le deviner.
