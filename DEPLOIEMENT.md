# Déploiement

Quatre conteneurs sur un serveur : Postgres, l'API, le worker Garmin, et Nginx qui sert
l'application et relaie `/api`. Aucun port de base de données n'est publié.

## 1. Prérequis

Docker et le plugin Compose sur le serveur, un nom de domaine qui pointe dessus, et de quoi
terminer le TLS — un proxy déjà en place, ou Caddy / Traefik / Nginx + certbot sur l'hôte.
La composition écoute en clair sur un port local (`WEB_PORT`, 8081 par défaut) : c'est le
proxy de l'hôte qui publie le HTTPS.

## 2. Configuration

```bash
git clone <dépôt> /opt/prepa && cd /opt/prepa
cp .env.prod.example .env

# Les trois secrets, à générer — ne jamais les reprendre d'un exemple :
openssl rand -base64 32   # DB_PASSWORD
openssl rand -base64 48   # JWT_SECRET
openssl rand -base64 32   # CRYPTO_KEY

chmod 600 .env
```

`CRYPTO_KEY` chiffre les identifiants Garmin au repos. **La changer rend illisibles ceux
déjà stockés** : il faudrait les ressaisir. Sauvegarde-la ailleurs que sur le serveur.

## 3. Premier démarrage

`WORKER_SERVICE_KEY` n'existe pas encore : laisse-la vide et démarre sans le worker.

```bash
docker compose -f docker-compose.prod.yml up -d --build postgres api web
docker compose -f docker-compose.prod.yml logs -f api   # les migrations s'appliquent ici
```

Crée le premier compte administrateur, puis coupe l'amorçage :

```bash
docker compose -f docker-compose.prod.yml stop api
docker compose -f docker-compose.prod.yml run --rm \
  -e PREPA_SEED_ENABLED=true \
  -e PREPA_SEED_ADMIN_EMAIL=toi@example.org \
  -e PREPA_SEED_ADMIN_PASSWORD='…' \
  api
```

Les journaux affichent **une seule fois** une clé de service. Note-la : c'est celle du
worker. Relance ensuite normalement.

Pour créer d'autres clés — une pour les skills, une par athlète si tu veux les cloisonner :

```bash
curl -X POST http://localhost:8081/api/v1/admin/service-keys \
  -H "Authorization: Bearer $JETON_ADMIN" -H 'Content-Type: application/json' \
  -d '{"nom":"claude-code","scopes":["read","coach"]}'
```

Renseigne `WORKER_SERVICE_KEY` dans `.env`, puis :

```bash
docker compose -f docker-compose.prod.yml up -d --build
```

## 4. Comptes et comptes Garmin

```bash
# Un athlète
curl -X POST .../api/v1/admin/athletes -H "Authorization: Bearer $JETON_ADMIN" \
  -H 'Content-Type: application/json' \
  -d '{"email":"thibaut@…","motDePasse":"…","displayName":"Thibaut"}'

# Son compte Garmin — chiffré avant d'être stocké
curl -X PUT .../api/v1/athletes/<athleteId>/garmin-credentials \
  -H "X-Service-Key: $CLE" -H 'Content-Type: application/json' \
  -d '{"email":"…","motDePasse":"…"}'
```

Au premier passage réussi, le compte Garmin connecté est mémorisé sur l'athlète. Aux
suivants, un compte qui ne correspond pas **arrête** la synchronisation au lieu d'écrire
les séances d'un athlète dans l'historique d'un autre.

Si Garmin demande une validation en deux étapes, le worker ne peut pas y répondre seul :
lance une première fois `python -m worker.sync --athlete <id>` en interactif depuis le
serveur pour établir le jeton, qui vaut ensuite environ un an.

## 5. Migration depuis l'ancienne application

L'ancienne arborescence `profiles/` ne fait plus partie de cette application : récupère-la
depuis la branche `main`, ou depuis une sauvegarde.

```bash
git worktree add /tmp/ancienne-app main    # ou n'importe quelle copie

python3 migration/import_legacy.py \
  --source /tmp/ancienne-app/profiles \
  --api https://prepa.example.org --key "$CLE_SERVICE" \
  --admin-email toi@example.org --admin-password '…' \
  --mot-de-passe '…'

python3 migration/verifier.py /tmp/ancienne-app/profiles   # « Migration conforme »
```

Les mots de passe Garmin ne sont pas dans git : ils vivaient dans `profiles/*/.env`,
gitignorés. Il faut les ressaisir (§4).

Le script dépose aussi `migration/sortie/memoire-a-relire-*.md` : le texte cumulatif de
commentaires de l'ancienne application, à relire et découper en notes de coach. Il n'est
pas importé tel quel, volontairement — c'est l'occasion de repartir sur une mémoire propre.

## 6. Tâches planifiées

```cron
15 3 * * * /opt/prepa/exploitation/sauvegarde.sh    >> /var/log/prepa-sauvegarde.log 2>&1
 0 4 * * * /opt/prepa/exploitation/sync-nocturne.sh >> /var/log/prepa-sync.log 2>&1
```

Le worker tourne en continu pour les demandes immédiates ; le passage de 4 h rattrape le
reste.

## 7. Vérifier les sauvegardes

**Avant de te reposer dessus**, et après chaque changement de schéma :

```bash
exploitation/restauration.sh sauvegardes/prepa-AAAAMMJJ-HHMMSS.sql.gz
```

Le script restaure dans une base jetable, compte les lignes de chaque table et nettoie
derrière lui. La production n'est jamais touchée.

Copie les archives **hors du serveur** — un disque qui meurt emporte la base et ses
sauvegardes s'ils vivent au même endroit.

## 8. Mise à jour

```bash
cd /opt/prepa && git pull
docker compose -f docker-compose.prod.yml up -d --build
```

Les migrations s'appliquent au démarrage de l'API. Elles ne sont qu'additives : une version
plus ancienne peut être redémarrée sans perdre de données.

## 9. Dépannage

| Symptôme | Où regarder |
|---|---|
| L'API ne démarre pas | `logs api` — souvent une migration ou un secret absent |
| Pas de nouvelles séances | `GET /athletes/<id>/sync-status` : `AUTH_ERROR` = identifiants à refaire, `IDENTITE_KO` = mauvais compte relié |
| L'application affiche une erreur d'authentification | Jeton expiré ; le renouvellement est automatique, sinon se reconnecter |
| Le worker boucle sur des 429 | Garmin limite le débit ; il reprendra au passage suivant |

Les journaux ne contiennent ni identifiant Garmin ni contenu de journal.
