# 00 — Vision produit & périmètre

## Pitch

> Un coach marathon numérique, disponible 24/7, qui construit ton plan, l'adapte automatiquement à ce que tu fais vraiment sur le terrain, et te répond quand tu as un doute. Tu importes tes séances Garmin, il fait le reste.

## Personas

### 1. Coureur amateur régulier — cible principale
- Court depuis 1–5 ans, 2–4 sorties/semaine, une Garmin au poignet.
- Prépare un objectif (semi ou marathon) sans budget pour un coach humain (~80–150 €/mois).
- **N'a pas envie** de lire un plan Excel ou de comprendre "pourquoi ma FRC me dit X".
- **Veut** : un plan clair, savoir si sa séance de la veille était bonne, un rappel de ce qu'il doit faire demain.

### 2. Coureur "geek" — early adopter
- Aime les données, connaît VMA / seuil / allure marathon.
- Va aussi utiliser le mode "Poser une question au coach" (chat).
- Sera la source de feedback qualitatif V1.

### 3. Admin / opérateur — nous
- Doit voir en temps réel la consommation LLM, les erreurs d'import, les abus.

## Périmètre V1 (MVP)

Fonctionnalités visibles utilisateur :

- **Inscription / connexion** (email + mot de passe, OAuth Google en option).
- **Onboarding** : questionnaire guidé (course visée, date, chrono, records récents, dispo hebdo, blessures, contraintes). Génère `objectifs` + `plan` via le LLM.
- **Import CSV Garmin** (drop d'un export standard Garmin Connect).
- **Dashboard** : *où j'en suis dans ma prépa*, séance du jour, semaine en cours, prochain rendez-vous clé.
- **Onglet Plan** : vue par semaine, statut de chaque séance.
- **Onglet Journal** : saisie libre par semaine (fatigue, humeur, douleurs).
- **Chat coach** : question libre en langage naturel → réponse contextualisée.
- **Mise à jour hebdo** : bouton "Faire le point de la semaine" → le back rapproche activités et plan, questionne les écarts majeurs, adapte les semaines suivantes, produit un rapport.
- **Compte & facturation** : plan Free (quotas limités) / Pro (payant).

Techniquement :

- Multi-utilisateur, multi-prépa par utilisateur.
- 1 seul LLM provider (Anthropic Claude — voir §01), abstrait derrière une interface.
- PostgreSQL + JSONB pour les documents `objectifs` / `plan`.
- Déploiement conteneurisé.

## Périmètre V2

- **OAuth Garmin Connect** — import automatique quotidien via Activity API + webhooks (voir §06).
- **Notifications** : email / push la veille d'une séance clé, après une séance ratée, à la fin de la semaine.
- **Multi-sport** léger : prise en compte vélo/natation en cross-training (déjà prévu par le coach existant).
- **Partage lecture seule** : envoyer un lien à son entraîneur humain / kiné.
- **Export PDF** du plan de la semaine.

## Périmètre V3 (à ne pas concevoir maintenant, mais à ne pas fermer)

- Autres providers LLM (OpenAI, Mistral) — l'abstraction `LlmClient` doit rester provider-agnostique.
- Coach spécifique trail / triathlon (nouveau système prompt, même moteur).
- App mobile (React Native ou PWA).

## Hors-scope explicite (ne pas implémenter)

- **Pas de wearable direct** hors Garmin en V1/V2 (pas de Polar/Suunto/Coros — importables via un converter CSV plus tard).
- **Pas de diagnostic médical** — l'app **oriente vers un pro** en cas de douleur, ne prescrit rien.
- **Pas de coach voix / vidéo**.
- **Pas de social / feed / comparaison entre utilisateurs**.
- **Pas de paiement à l'usage** (crédits LLM revendus à l'utilisateur) — abonnement mensuel avec quotas, point.

## Critères de succès V1

- Un utilisateur peut créer un compte, générer un plan et importer un CSV **en moins de 5 minutes**, sans lire de doc.
- Le **coût LLM moyen** par utilisateur actif Free reste **< 0,15 €/mois** (voir §05 pour le budget de tokens).
- Une question au coach répond **en moins de 8 s** (p95).
- Zéro donnée sensible en clair dans les logs (voir §08).
