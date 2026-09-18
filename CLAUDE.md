# prepa-app

Suivi d'entraînement en course à pied, **multi-athlètes**, avec une dimension coach. Une
application web adossée à une API et à une base Postgres ; le coach, lui, reste Claude Code,
qui lit et écrit par cette API.

## Le modèle : des cycles, pas seulement des prépas

L'unité de travail est le **cycle** — une période d'entraînement continue avec une intention.
Il a deux formes :

- **`PREPA`** — une course à une date, un chrono visé, un plan complet jusqu'au jour J.
- **`LIBRE`** — pas de course en vue : une **ligne directrice** (« maintenir la charge »,
  « progresser en VO2max », « passer au trail ») et un **horizon** en semaines.

Même structure de semaines et de séances dans les deux cas. Ce qui diffère, c'est la
planification : en cycle libre, le plan est **glissant** — trois à quatre semaines détaillées
séance par séance, le reste en cibles hebdomadaires jusqu'à l'horizon. Planifier une séance à
deux mois sans échéance qui l'impose serait une fausse précision.

Un athlète a **au plus un cycle actif**. Les activités, elles, sont rattachées à l'athlète et
traversent les cycles : c'est ce qui permet de comparer une période à une autre.

## Structure

```
prepa-app/
├── coach/COACH.md          la méthodologie — fait autorité
├── prepa-api/              API Spring Boot 4 / Java 21
├── prepa-web/              application Vue 3
├── cli/prepa               client des skills
├── migration/              import depuis l'ancienne application
├── exploitation/           sauvegarde, restauration, déploiement, MAINTENANCE.md
└── .claude/skills/         prepa-cycle, prepa-update
```

## Rôle coach — important

Pour **toute question d'entraînement, d'allure, d'adaptation ou de comportement de coach**,
lis d'abord [`coach/COACH.md`](coach/COACH.md). C'est le cerveau du projet : principes,
calcul des allures, règles d'adaptation, protocole douleur, cycles libres, et les cinq
situations qu'on ne traite **jamais** sans demander la cause à l'athlète.

## Le cycle de vie d'une séance

Trois temps, et la distinction tient à **qui pose le statut** :

| Statut | Posé par | Quand |
|---|---|---|
| `A_VENIR` | — | par défaut |
| `REALISEE` | **le système** | une activité Garmin correspond à la séance |
| `NON_REALISEE` | **le système** | la date est passée, rien n'est venu (un jour de battement) |
| `ANALYSEE` | **le coach** | il l'a regardée et commentée |
| `DEPLACEE` / `ANNULEE` | selon le cas | reportée dans la semaine, ou retirée du plan |

`REALISEE` et `NON_REALISEE` sont des **constats** ; `ANALYSEE` est un **jugement**. Les
deux constats n'arrivent pas par le même chemin, parce qu'ils ne s'apprennent pas de la
même façon : qu'une séance ait eu lieu se sait **à l'ingestion**, quand l'activité arrive ;
qu'elle n'ait *pas* eu lieu ne découle d'aucun événement — c'est le temps qui passe qui le
dit, et un **passage quotidien** le constate, un jour de battement laissé aux montres. Dans
les deux cas l'athlète voit sa semaine à jour sans attendre le point hebdomadaire, et le
coach n'a pas à cocher à la main ce que les données disent déjà.

Il n'existe volontairement pas de statut « manquée » automatique : déclarer une séance
manquée avant d'avoir demandé à l'athlète ce qui s'est passé reviendrait à juger sans savoir.
Le constat dit qu'il ne s'est rien passé ; la raison vit dans le commentaire du coach.

Un constat n'écrase jamais une décision déjà prise : une séance analysée, déplacée ou annulée
ne bouge plus.

## Qui écrit quoi

La ligne de partage est portée par le code, pas par une convention : *l'athlète décrit la
réalité, il ne redessine pas l'entraînement.*

| | Athlète (application) | Coach (clé de service) |
|---|---|---|
| Marquer une séance faite ou non faite (sortie sans montre) | ✅ | ✅ |
| Commenter une séance, tenir son journal | ✅ | ✅ (commentaire de coach) |
| Décaler une séance **dans sa semaine** | ✅ | ✅ (n'importe où) |
| Saisir un ressenti, signaler une blessure | ✅ | ✅ |
| Type, allures, distance cible d'une séance | ❌ | ✅ |
| Ajouter ou supprimer une séance, cibles hebdo | ❌ | ✅ |
| Créer, convertir, clôturer un cycle | ❌ | ✅ |
| Écrire une note de coach | ❌ | ✅ |

Une tentative interdite renvoie `403 FORBIDDEN_FIELD` **en nommant le champ**, plutôt qu'un
refus opaque.

### Les athlètes se voient entre eux, en lecture

Une deuxième ligne traverse la première : *l'entraînement se regarde, la personne non.* Tout
athlète connecté lit le plan, les séances, les sorties et les analyses des autres — se
comparer fait partie de l'entraînement. Il n'y écrit rien, pas même les constats qu'il pose
chez lui : confirmer une sortie faite sans montre reste au coureur qui l'a faite.

Restent fermés, en lecture comme en écriture, le **journal**, le **profil sportif**, les
**blessures et contraintes**, les **comptes Garmin** et le **`coach-context`** — qui agrège
précisément les trois premiers. La liste des athlètes ne livre d'ailleurs que le nom des
autres : leur email et leur compte Garmin les identifient ailleurs qu'ici.

Le code porte la distinction en deux permissions, `peutLire` et `peutModifier`, servies par
`AthleteService.lisible(...)` et `.modifiable(...)`. Une route nouvelle choisit donc son
garde — et `modifiable` est le bon défaut : c'est l'ouverture qui se décide, pas la
fermeture. Une clé de service, elle, ne gagne rien à cette ouverture : nominative, elle
reste bornée à son athlète, faute de quoi un skill ouvert pour l'un lirait l'historique de
tous.

## Développement

```bash
docker compose up -d                    # Postgres sur :5433
cd prepa-api && ./mvnw spring-boot:run  # API sur :8080
cd prepa-web && npm run dev             # application sur :5173
```

Démarrage détaillé, comptes et import des données : [DEMARRAGE.md](DEMARRAGE.md).

```bash
cd prepa-api && ./mvnw test     # Postgres réel via Testcontainers
cd prepa-web && npm run build   # vérifie aussi les types
```

## Les skills

Deux, et deux seulement :

- **`/prepa-cycle`** — ouvrir un cycle, basculer de libre à prépa, clôturer.
- **`/prepa-update`** — le point de la semaine, à lancer chaque semaine. Rapatrie les
  dernières séances, lit le contexte et les écarts déjà qualifiés, questionne, adapte,
  enregistre le bilan.

Le sync Garmin n'a plus son propre skill : il tourne seul chaque nuit sur le serveur, et
`/prepa-update` le déclenche au démarrage si l'athlète a couru entre-temps.

Ils passent par `cli/prepa` ([documentation](cli/README.md)), configuré dans
`~/.prepa-cli/config.json`.

## Endpoints qui portent le métier

| Endpoint | Ce qu'il donne |
|---|---|
| `GET /athletes/{id}/coach-context` | l'état de l'athlète en un appel **borné** — profil, blessures, cycle, semaine, mémoire du coach, journal récent |
| `GET /athletes/{id}/reconciliation` | prévu contre réalisé, **écarts déjà qualifiés** |
| `GET /athletes/{id}/analysis` | volume hebdo, allure d'endurance réelle, meilleurs efforts, tendances |
| `GET /athletes/{id}/activities/new` | séances jamais passées en revue, bornées au cycle |
| `GET /cycles/{id}/skeleton` | trame de charge d'un cycle libre |
| `PATCH /weeks/{id}` | ce qu'une semaine **vise** — volume cible, bloc, note — sans rejouer tout le plan |

### Les trois volumes d'une semaine

Ils ne disent pas la même chose, et les confondre fait perdre l'information qui compte :

| | D'où il vient | Ce qu'il dit |
|---|---|---|
| `volumeCibleKm` | **posé** par le coach | ce que la semaine vise. Seule information d'une semaine non détaillée |
| `volumePlanifieKm` | **calculé** sur les séances | ce que le plan détaille, hors séances annulées |
| volume réalisé | **calculé** sur les activités | ce qui a été couru — dans `reconciliation` et `analysis` |

La cible ne se recalcule pas quand une séance change de distance : rallonger une sortie longue
de deux kilomètres ne doit pas réécrire en silence l'intention de la semaine. L'écart entre les
deux premiers est rendu visible pour que le coach tranche — reprendre les kilomètres ailleurs,
ou assumer la nouvelle charge avec `PATCH /weeks/{id}`.

## Les écrans

| Onglet | Ce qu'on y cherche |
|---|---|
| **Aujourd'hui** | la séance du jour, la semaine en cours jour par jour, ce qui reste à confirmer |
| **Saison** | l'année en un coup d'œil : calendrier des sorties, volume mensuel, cycles, échéances |
| **Séances** | le déroulé d'une sortie — allure, fréquence cardiaque et relief sur le même graphe |
| **Stats** | volume hebdomadaire contre objectifs, meilleurs efforts, dérive cardiaque |
| **Journal** | du texte libre, rien d'autre |

Un sixième écran n'apparaît qu'aux comptes `ADMIN` : **Synchronisation** (`#/admin/sync`),
qui montre l'état des comptes Garmin reliés et l'historique des passages.

Le semainier de l'onglet Aujourd'hui se parcourt avec les flèches : il n'y a pas d'écran
« plan » séparé, qui redonnait la même information une deuxième fois.

Le nom en tête de page devient un **sélecteur de profil** dès qu'il y a quelqu'un d'autre à
regarder. Consulter un autre athlète affiche un bandeau « lecture seule », retire les gestes
de confirmation et masque l'onglet Journal — qui, resté ouvert, aurait montré le sien sous le
nom d'un autre.

## La mémoire du coach

Les décisions deviennent des `coach_notes` **datées, typées et bornées** — plus un champ de
commentaires qui grossit sans fin. Trois portées : `DURABLE` (règle permanente), `CYCLE`
(vraie pour cette préparation), `PONCTUELLE` (purgée après huit semaines). Contenu plafonné à
**500 caractères** : une décision qui n'y tient pas est mal formulée. Une décision qui en
annule une autre la désactive explicitement, plutôt que de laisser deux consignes
contradictoires.

## Synchronisation Garmin

C'est l'API qui va chercher les séances, sur sa propre cadence : un passage complet toutes
les trente minutes, et une relève des demandes chaque minute. Il n'y a plus de processus
tiers — les identifiants et les jetons sont chiffrés au repos (AES-GCM) et ne sortent jamais
de la base.

Le jeton de longue durée obtenu à la première connexion vaut environ un an. C'est lui qui
évite de rejouer le mot de passe à chaque passage, ce que Garmin finit par sanctionner en
exigeant une vérification en deux étapes. Le cas échéant, la synchronisation s'arrête sur
`MFA_REQUISE` et `POST /athletes/{id}/garmin-mfa` la débloque.

**Garde-fou d'identité** : au premier passage réussi, le compte Garmin est mémorisé sur
l'athlète ; aux suivants, un compte différent arrête la synchronisation au lieu d'écrire les
séances d'un athlète dans l'historique d'un autre.

Chaque passage laisse une trace datée, consultable sur l'**écran d'administration**
(`#/admin/sync`, réservé au rôle `ADMIN`) : ce qui tourne, l'état de chaque compte relié,
l'historique, et de quoi relancer.

## Déploiement et exploitation

[DEPLOIEMENT.md](DEPLOIEMENT.md) pour l'installation,
[exploitation/MAINTENANCE.md](exploitation/MAINTENANCE.md) pour le quotidien — accès au
serveur, requêtes SQL utiles, dépannage de la synchronisation.

