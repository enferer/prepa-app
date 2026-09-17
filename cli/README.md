# `prepa` — client en ligne de commande

Passerelle des skills Claude Code vers l'API. Les skills l'appellent plutôt que de composer
des requêtes à la main : les identifiants restent hors des conversations, et la sortie est
stable à lire.

## Installation

```bash
mkdir -p ~/.prepa-cli
cat > ~/.prepa-cli/config.json <<'JSON'
{
  "apiUrl": "https://prepa.example.org",
  "serviceKey": "psk_…",
  "athleteParDefaut": "Thibaut"
}
JSON
chmod 600 ~/.prepa-cli/config.json
```

La clé de service se crée côté serveur :

```bash
curl -X POST "$API/api/v1/admin/service-keys" \
  -H "Authorization: Bearer $JETON_ADMIN" -H 'Content-Type: application/json' \
  -d '{"nom":"claude-code","scopes":["read","coach"]}'
```

Elle n'est affichée qu'une fois. Une clé nominative (`athleteId` renseigné) est limitée à
cet athlète ; sans lui, elle vaut pour tous.

## Commandes

| Commande | Ce qu'elle donne |
|---|---|
| `prepa athletes` | les athlètes accessibles à la clé |
| `prepa contexte` | le paquet de départ d'un point hebdomadaire, borné |
| `prepa analyse --jours 90` | volume, allures réelles, efforts notables, tendances |
| `prepa rapprochement [--semaine AAAA-MM-JJ]` | prévu contre réalisé, écarts qualifiés |
| `prepa nouvelles` | séances jamais passées en revue, avec leur déroulé |
| `prepa cycles` | les cycles de l'athlète |
| `prepa plan <cycleId>` | le plan complet d'un cycle |
| `prepa GET\|POST\|PUT\|PATCH <chemin> [json]` | tout le reste |

`--athlete <nom>` cible un athlète ; sans lui, c'est `athleteParDefaut`, ou le seul athlète
accessible.

## Exemples

```bash
prepa contexte --athlete camille
prepa rapprochement --semaine 2026-09-07
prepa PATCH /sessions/<id> '{"statut":"VALIDEE","commentaireCoach":"Allures tenues."}'
prepa POST /athletes/<id>/coach-notes '{"portee":"CYCLE","categorie":"DECISION","titre":"…","contenu":"…"}'
```
