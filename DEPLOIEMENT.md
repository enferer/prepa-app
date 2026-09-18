# Déploiement

Trois conteneurs sur un serveur : Postgres, l'API — qui porte aussi la synchronisation
Garmin — et Nginx qui sert l'application et relaie `/api`. **Seul le port de l'application est publié** — ni la base,
ni l'API ne sont joignables directement de l'extérieur.

Une fois en place, le quotidien est décrit dans
[exploitation/MAINTENANCE.md](exploitation/MAINTENANCE.md).

## 1. Prérequis

Docker et le plugin Compose, et un port libre sur l'hôte (`WEB_PORT`, 8080 par défaut).

L'application est servie **en clair** : le mot de passe de connexion circule sans
chiffrement. C'est tenable sur un usage personnel, mais si l'outil sort de ce cadre, un
terminateur TLS devant `WEB_PORT` est la première chose à ajouter.

### Cohabitation avec un autre service

Ce serveur héberge déjà `firework-timeline`, qui occupe le port 80. Rien ici ne s'en
approche :

| | `firework-timeline` | `prepa` |
|---|---|---|
| Port publié | 80 | 8080 (`WEB_PORT`) |
| Réseau Docker | le sien | le sien |
| Base | son volume | son volume |

La machine est petite : 1 cœur, 2 Go de mémoire, partagés. Chaque conteneur porte donc
une limite de mémoire explicite dans `docker-compose.prod.yml`, et la JVM un plafond de
tas. Ce n'est pas du réglage fin, c'est ce qui empêche un service d'emporter l'autre.

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

`PREPA_SERVICE_KEY` n'existe pas encore : laisse-la vide, elle ne sert qu'aux skills.

```bash
docker compose -f docker-compose.prod.yml up -d --build postgres api
docker compose -f docker-compose.prod.yml logs -f api   # les migrations s'appliquent ici
```

Crée le premier compte administrateur. L'amorçage se fait au démarrage d'une instance
jetable, et le mot de passe est demandé plutôt qu'écrit dans la commande — sans quoi il
resterait dans l'historique du shell :

```bash
cd /opt/prepa
read -rsp 'Mot de passe admin       : ' MDP  && echo
read -rsp 'Confirme le mot de passe : ' MDP2 && echo
[ "$MDP" = "$MDP2" ] || echo 'LES DEUX SAISIES DIFFERENT — ne va pas plus loin'

timeout 150 docker compose -f docker-compose.prod.yml run --rm --no-deps \
  -e PREPA_SEED_ENABLED=true \
  -e PREPA_SEED_ADMIN_EMAIL=toi@example.org \
  -e PREPA_SEED_ADMIN_USERNAME=toi \
  -e PREPA_SEED_ADMIN_PASSWORD="$MDP" \
  api 2>&1 | grep -B3 -A3 'Cle de service'
```

La double saisie n'est pas du zèle : le mot de passe est tapé en aveugle dans un
conteneur jetable, et rien ne le revérifie ensuite. Une faute de frappe ne se découvre
qu'au premier usage — souvent bien plus tard. Vérifie donc tout de suite :

```bash
printf '%s' "$MDP" | python3 -c "
import json, sys, urllib.request, urllib.error
corps = json.dumps({'identifiant': 'toi@example.org', 'motDePasse': sys.stdin.read()}).encode()
requete = urllib.request.Request('http://localhost:8080/api/v1/auth/login', corps,
                                 {'Content-Type': 'application/json'})
try:
    urllib.request.urlopen(requete)
    print('Connexion OK')
except urllib.error.HTTPError as e:
    print('ECHEC', e.code)
"
unset MDP MDP2
```

### Se tromper de mot de passe

Tant que la base ne contient que ce compte, le plus simple est de le refaire : l'amorçage
s'abstient dès qu'un athlète existe, il faut donc d'abord effacer.

```bash
docker compose -f docker-compose.prod.yml exec -T postgres \
  psql -U prepa -d prepa -c 'delete from athletes'
```

Cela efface **tout athlète et tout ce qui en dépend**. Une fois des données migrées, ce
n'est plus une option : il faut alors passer par un administrateur encore accessible.

Les journaux affichent **une seule fois** une clé de service portant tous les droits.
Note-la. L'amorçage se coupe tout seul — il n'est actif que sur cette instance jetable, et
il s'abstient dès que la base contient un athlète.

Pour créer d'autres clés — une par athlète si tu veux les cloisonner :

```bash
docker compose -f docker-compose.prod.yml exec api \
  wget -qO- --post-data '{"nom":"claude-code","scopes":["read","coach"]}' \
  --header "Authorization: Bearer $JETON_ADMIN" \
  --header 'Content-Type: application/json' \
  http://localhost:8080/api/v1/admin/service-keys

# ou depuis l'exterieur :
curl -X POST http://vps-75154aed.vps.ovh.net:8080/api/v1/admin/service-keys \
  -H "Authorization: Bearer $JETON_ADMIN" -H 'Content-Type: application/json' \
  -d '{"nom":"claude-code","scopes":["read","coach"]}'
```

Renseigne `PREPA_SERVICE_KEY` dans `.env`, puis :

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

Si Garmin demande une validation en deux étapes, la synchronisation s'arrête sur
`MFA_REQUISE` et met la session de côté quelques minutes. Récupère le code sur le téléphone
ou dans la boîte mail du compte Garmin, puis transmets-le :

```bash
curl -X POST http://vps-75154aed.vps.ovh.net:8080/api/v1/athletes/<id>/garmin-mfa \
  -H "X-Service-Key: $CLE" -H 'Content-Type: application/json' \
  -d '{"code":"123456"}'
```

Le jeton ainsi obtenu vaut environ un an : la question ne se reposera pas avant longtemps.
Passé le délai, la demande expire — relance une synchronisation pour en obtenir une
nouvelle.

## 5. Migration depuis l'ancienne application

L'ancienne arborescence `profiles/` ne fait plus partie de cette application : récupère-la
depuis la branche `main`, ou depuis une sauvegarde.

```bash
git worktree add /tmp/ancienne-app main    # ou n'importe quelle copie

read -rsp 'Mot de passe des comptes athletes : ' MDP && echo
python3 migration/import_legacy.py \
  --source /tmp/ancienne-app/profiles \
  --api http://vps-75154aed.vps.ovh.net:8080 --key "$CLE_SERVICE" \
  --admin-email toi@example.org \
  --mot-de-passe "$MDP"
unset MDP

python3 migration/verifier.py /tmp/ancienne-app/profiles   # « Migration conforme »
```

Le mot de passe administrateur est demandé à la saisie : en argument, il resterait dans
l'historique du shell et s'afficherait dans la liste des processus.

```bash
```

Les mots de passe Garmin ne sont pas dans git : ils vivaient dans `profiles/*/.env`,
gitignorés. Il faut les ressaisir (§4).

Le script dépose aussi `migration/sortie/memoire-a-relire-*.md` : le texte cumulatif de
commentaires de l'ancienne application, à relire et découper en notes de coach. Il n'est
pas importé tel quel, volontairement — c'est l'occasion de repartir sur une mémoire propre.

## 6. Synchronisation Garmin et tâches planifiées

L'API porte elle-même sa cadence, et n'a **pas besoin du planificateur de la machine** :

- toutes les **30 minutes**, un passage complet sur tous les athlètes reliés ;
- toutes les minutes, la relève des demandes laissées en suspens — un filet, pour le cas
  où le serveur aurait redémarré en plein passage ;
- chaque nuit à 4h45, la purge des traces de plus de 90 jours ;
- chaque matin à 5h30, le constat des séances que rien n'est venu accomplir.

Les quatre cadences se règlent sous `prepa.sync` et `prepa.constats` dans
`application.yml`.

**L'écran d'administration** (`#/admin/sync`, réservé au rôle `ADMIN`) montre ce qui tourne,
l'état de chaque compte relié et l'historique des passages, et permet de relancer — tout le
monde ou un athlète précis. C'est le premier endroit à ouvrir quand des séances ne
remontent plus.

Seule la sauvegarde est planifiée par la machine. Cette machine n'a pas de `cron` ; elle est portée par
un minuteur systemd, installé une fois :

```bash
systemctl list-timers prepa-sauvegarde   # prochaine exécution
sudo systemctl start prepa-sauvegarde    # forcer une sauvegarde
journalctl -u prepa-sauvegarde -n 20     # ce qu'elle a fait
```

Le minuteur est `Persistent` : une sauvegarde manquée parce que le serveur était éteint
est rattrapée au démarrage, plutôt que sautée en silence.

`exploitation/sync-nocturne.sh` subsiste pour forcer un passage complet à la main.

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
/opt/prepa/exploitation/deploiement.sh            # la branche en place
/opt/prepa/exploitation/deploiement.sh refonte-v2 # en changer
```

Le script refuse de partir s'il trouve des modifications non commitées sur le serveur,
**sauvegarde la base avant de basculer**, ne fait qu'une avance rapide sur la branche
demandée, reconstruit, redémarre, puis attend que l'API réponde `UP` — et déverse les
journaux si elle ne revient pas. Il purge enfin les images intermédiaires : sur un petit
disque, c'est ce qui fait échouer le déploiement suivant.

Les migrations s'appliquent au démarrage de l'API. Elles ne sont qu'additives : une version
plus ancienne peut être redémarrée sans perdre de données.

## 9. Dépannage

| Symptôme | Où regarder |
|---|---|
| L'API ne démarre pas | `logs api` — souvent une migration ou un secret absent |
| Pas de nouvelles séances | L'écran `#/admin/sync`, ou `GET /athletes/<id>/sync-status` : `AUTH_ERROR` = identifiants à refaire, `IDENTITE_KO` = mauvais compte relié |
| L'application affiche une erreur d'authentification | Jeton expiré ; le renouvellement est automatique, sinon se reconnecter |
| Une synchronisation échoue sur un 429 | Garmin limite le débit ; le passage suivant reprendra. Augmenter `GARMIN_PAUSE_MS` si cela se répète |
| Garmin réclame un code | Statut `MFA_REQUISE` : voir §4 |
| Le site ne répond pas | `logs web`, puis vérifier que `WEB_PORT` n'est pas pris par un autre service |
| Un conteneur est tué sans raison | `docker inspect <nom> --format '{{.State.OOMKilled}}'` : la mémoire est partagée avec l'autre service |

Les journaux ne contiennent ni identifiant Garmin ni contenu de journal.
