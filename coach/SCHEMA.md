# SCHEMA.md — Contrat des fichiers `data/`

> Ce document définit **la forme exacte** des fichiers JSON que les skills génèrent et que la vue (`vue/js/app.js`) consomme. La vue ne lit **que** ces structures : s'en écarter casse l'affichage, souvent en silence. `scripts/build_data.py` valide ce contrat à chaque génération (voir `valider_plan` / `valider_objectifs`) et signale les écarts. En cas de doute, cette page fait foi.

## ⚠️ Deux pièges qui reviennent

1. **Le plan est groupé par semaine, PAS une liste plate.** La vue itère sur `plan.semaines[]`, chaque semaine contenant ses `seances[]`. Une clé `seances` à la racine du plan est ignorée → dashboard et onglet Plan vides.
2. **`alluresCibles` d'une séance est une CHAÎNE**, pas un objet. La vue fait `escapeHtml(seance.alluresCibles)` : lui passer un objet lève une exception et interrompt le rendu (sans erreur console visible). Pour un Renfo, on **omet** `alluresCibles` et on renseigne `focus`.

---

## `objectifs.json`

Objet unique. Champs clés lus par la vue et utiles au coach :

```jsonc
{
  "course":   { "nom": "Marathon", "distanceKm": 42.195, "date": "2026-10-25" },  // date ISO obligatoire
  "chronoVise": "4h00",
  "chronoViseSec": 14400,
  "dateDebutPrepa": "2026-07-12",
  "nbSemaines": 15,
  "frequenceSeancesParSemaine": 4,
  "volumeDepartKmSemaine": 32,
  "references": { "rp10km": "…", "semi": "…", "plusLongueSortie": "…" },
  "alluresCibles": {                       // OBJET ici (≠ des séances du plan)
    "EF":    { "secKm": 400, "affichage": "6:40", "note": "…" },
    "SL":    { "secKm": 380, "affichage": "6:20" },
    "AM":    { "secKm": 341, "affichage": "5:41" },
    "Seuil": { "secKm": 320, "affichage": "5:20" },
    "VMA":   { "secKm": 270, "affichage": "4:30" }
  },
  "blessures": [ { "zone": "…", "statut": "…", "consignes": "…" } ],
  "contraintes": [ { "type": "vacances", "periode": "…", "detail": "…" } ],
  "seancesFavorites": [ { "nom": "…", "type": "Cotes", "description": "…",
                          "distanceKm": 8, "frequenceSouhaitee": "…", "contexte": "…" } ],
  "renforcement": { "actif": true, "frequenceParSemaine": 2,
                    "materiel": ["Poids du corps", "Elastiques"], "focus": "…" },
  "commentairesLibres": "…"
}
```

Note : `alluresCibles` au niveau **objectifs** est un objet `{ zone: {secKm, affichage} }` (affiché dans l'onglet Stats). Au niveau **séance** du plan, c'est une **chaîne** (voir ci-dessous). Ne pas confondre les deux.

---

## `plan.json`

```jsonc
{
  "course": { "nom": "Marathon", "date": "2026-10-25", "chronoVise": "4h00" },
  "dateDebutPrepa": "2026-07-12",
  "nbSemaines": 15,
  "blocs": [ { "nom": "Base / fondation", "semaines": "1-5", "objectif": "…" } ],  // indicatif
  "semaines": [ /* … objets Semaine, voir ci-dessous … */ ]
}
```

### Objet **Semaine** (obligatoire dans `semaines[]`)

| Champ | Type | Obligatoire | Rôle |
|---|---|---|---|
| `numero` | entier | ✅ | numéro de semaine (1, 2, …) |
| `dateDebut` | date ISO `YYYY-MM-DD` | ✅ | **lundi** de la semaine ; sert à situer « semaine en cours » et à additionner le km réalisé |
| `volumeCibleKm` | nombre | ✅ | volume course cible (hors renfo) ; sommé pour la cible totale |
| `bloc` | chaîne | recommandé | libellé du bloc affiché (« Base / fondation ») |
| `note` | chaîne | optionnel | note de semaine affichée en tête |
| `seances` | tableau d'objets Séance | ✅ | voir ci-dessous |

### Objet **Séance**

| Champ | Type | Obligatoire | Rôle |
|---|---|---|---|
| `date` | date ISO `YYYY-MM-DD` | ✅ | jour de la séance (dans la fenêtre de sa semaine) |
| `type` | chaîne | ✅ | `EF`, `SL`, `Seuil`, `VMA`, `AM`, `Cotes`, `Renfo`, `Marathon`… (pilote l'icône) |
| `titre` | chaîne | ✅ | titre court |
| `description` | chaîne | recommandé | détail de la séance |
| `statut` | chaîne | ✅ | l'un de `a_venir`, `validee`, `adaptee`, `modifiee`, `manquee` |
| `commentaireCoach` | chaîne | recommandé | justification / consigne du coach |
| `alluresCibles` | **chaîne** | course : recommandé | ex. `"Seuil 5:20 · EF 6:40"`. **Objet interdit.** À **omettre** pour un Renfo. |
| `distanceCibleKm` | nombre | course | distance cible (compte dans le volume) |
| `dureeCibleMin` | nombre | renfo | durée cible (pour les séances sans distance) |
| `focus` | chaîne | renfo | objectif du renfo (affiché à la place des allures) |

Statuts « faits » (comptés comme réalisés / assiduité) : `validee`, `adaptee`, `modifiee`. `a_venir` = à faire, `manquee` = ratée.

Séance **course** : porter `distanceCibleKm` + `alluresCibles` (chaîne). Séance **Renfo** : porter `dureeCibleMin` + `focus`, **sans** `alluresCibles` ni `distanceCibleKm`.

---

## Générer un plan proprement

`scripts/build_data.py` valide automatiquement ces règles et affiche `❌ VALIDATION …` en cas d'écart : **toujours vérifier sa sortie** après régénération. Le générateur peut produire les séances comme une liste plate en interne, mais **doit les regrouper en `semaines[]`** (avec `numero`, `dateDebut` = lundi, `volumeCibleKm`) avant d'écrire `plan.json`, et sérialiser `alluresCibles` en chaîne (en l'omettant pour les Renfo).
