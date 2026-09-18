# Démarrer en local

Trois terminaux, trois commandes. Il faut Docker, Java 21 et Node 22.

## 1. La base

```bash
docker compose up -d
```

Postgres 16 sur le **port 5433** (et non 5432, pour ne pas gêner une autre installation).
Base `prepa`, utilisateur `prepa`, mot de passe `prepa`. Les données survivent aux
redémarrages ; `docker compose down -v` les efface.

## 2. L'API

```bash
cd prepa-api && ./mvnw spring-boot:run
```

Sur `http://localhost:8080`. Les migrations s'appliquent au démarrage — il n'y a rien à
préparer. Vérification : `curl localhost:8080/actuator/health`.

## 3. L'application

```bash
cd prepa-web && npm install && npm run dev
```

Sur **`http://localhost:5173`**. C'est l'adresse à ouvrir. Les appels `/api` sont relayés
vers le port 8080 : pas de question de CORS.

---

## Se connecter

| Compte | Mot de passe | Ce qu'il voit |
|---|---|---|
| `thibaut@prepa.local` | `motdepasse123` | sa prépa marathon, 254 activités |
| `camille@prepa.local` | `motdepasse123` | sa prépa, 142 activités |
| `admin@prepa.local` | `motdepasse123` | idem + création de comptes et de clés |

Ces comptes viennent de la migration. **Mots de passe de développement** — à changer au
déploiement.

## Si la base est vide

Au tout premier démarrage, ou après un `down -v` :

```bash
# 1. Créer le compte administrateur et une clé de service
cd prepa-api && ./mvnw spring-boot:run \
  -Dspring-boot.run.arguments="--prepa.seed.enabled=true \
    --prepa.seed.admin-email=admin@prepa.local \
    --prepa.seed.admin-password=motdepasse123"
```

Les journaux affichent **une seule fois** une clé `psk_…`. Note-la.

```bash
# 2. Importer les données de l'ancienne application, restée sur main
git worktree add /tmp/ancienne-app main

python3 migration/import_legacy.py \
  --source /tmp/ancienne-app/profiles \
  --key "psk_…" --admin-password motdepasse123 --mot-de-passe motdepasse123

python3 migration/verifier.py /tmp/ancienne-app/profiles   # « Migration conforme »
```

Relance ensuite l'API sans les arguments de démarrage : le compte existe.

## Faire tourner les skills

```bash
mkdir -p ~/.prepa-cli && cat > ~/.prepa-cli/config.json <<JSON
{
  "apiUrl": "http://localhost:8080",
  "serviceKey": "psk_…",
  "athleteParDefaut": "Thibaut"
}
JSON
```

Puis `./cli/prepa contexte` pour vérifier, et `/prepa-update` dans Claude Code.

Le sync Garmin tourne en local dès qu'un compte est relié — c'est l'API qui s'en charge.
Sans compte relié, `/prepa-update` le signalera et travaillera sur les données déjà en base.

## Tests

```bash
cd prepa-api && ./mvnw test      # 100 tests, Postgres réel via Testcontainers
cd prepa-web && npm run build    # vérifie aussi les types
```

Les tests lancent leur propre conteneur : ils ne touchent pas la base de développement.

## Ça ne marche pas

| Symptôme | Cause probable |
|---|---|
| L'API refuse de démarrer | Postgres pas prêt — `docker compose ps` doit dire `healthy` |
| `Connection refused` sur 5433 | `docker compose up -d` oublié |
| La page reste sur la connexion | API éteinte ; regarde le terminal de `spring-boot:run` |
| Identifiants refusés | La base a été effacée — voir « Si la base est vide » |
| Port 8080 déjà pris | `SERVER_PORT=8081 ./mvnw spring-boot:run`, et ajuste le proxy dans `vite.config.ts` |
