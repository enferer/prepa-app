# 04 — Frontend Vue.js

## Principe UX directeur

**L'athlète ne doit jamais avoir l'impression d'utiliser un logiciel technique.** Il utilise un coach.

Concrètement :
- La home après login **ne demande pas de choisir** — elle affiche directement le dashboard de la prépa active.
- **Aucun champ JSON** exposé. Les allures s'affichent en `m:ss/km`, pas en secondes.
- Trois actions primaires seulement, toujours visibles : **Importer** — **Poser une question** — **Faire le point de la semaine**.
- Les concepts techniques (quotas, plan version, sync Garmin) sont **relégués aux Réglages**.
- Le mobile est first-class : la vue actuelle a déjà un mode compact — on garde cette rigueur.

## Stack

- Vue 3.4+ Composition API, TypeScript strict.
- Vite, Vue Router 4, Pinia 2.
- TailwindCSS 3 + Headless UI (dialogs, menus accessibles gratuits).
- Axios instance unique (`src/api/http.ts`) : baseURL env, interceptor JWT, refresh transparent sur 401, gestion 429 (toast "Tu as atteint ta limite mensuelle, upgrade en Pro").
- SSE via `EventSource` natif pour le streaming des réponses chat.

## Arborescence

```
prepa-coach-web/
├── vite.config.ts
├── src/
│   ├── main.ts
│   ├── router/
│   │   └── index.ts           # guards auth + preload prepa active
│   ├── stores/
│   │   ├── auth.ts            # user, tokens, plan, quotas
│   │   ├── prepa.ts           # prepa active, plan, activities, journal
│   │   ├── chat.ts            # sessions, messages
│   │   └── ui.ts              # toasts, modales
│   ├── api/
│   │   ├── http.ts
│   │   ├── auth.ts            # register, login, refresh
│   │   ├── prepas.ts
│   │   ├── activities.ts
│   │   ├── coach.ts
│   │   └── journal.ts
│   ├── views/
│   │   ├── auth/
│   │   │   ├── LoginView.vue
│   │   │   ├── RegisterView.vue
│   │   │   └── VerifyEmailView.vue
│   │   ├── onboarding/
│   │   │   └── OnboardingWizard.vue    # étape par étape → POST /prepas
│   │   ├── DashboardView.vue           # écran d'accueil
│   │   ├── PlanView.vue                # plan complet, semaine par semaine
│   │   ├── StatsView.vue               # graphes agrégés
│   │   ├── JournalView.vue
│   │   ├── ChatView.vue                # coach conversationnel
│   │   ├── SettingsView.vue
│   │   └── admin/…
│   ├── components/
│   │   ├── prepa/
│   │   │   ├── SeanceCard.vue          # rendu d'une séance (statut, allures, coach)
│   │   │   ├── WeekBlock.vue           # une semaine du plan
│   │   │   ├── VolumeChart.vue
│   │   │   └── AllureBadge.vue
│   │   ├── coach/
│   │   │   ├── ChatComposer.vue
│   │   │   ├── ChatMessage.vue
│   │   │   └── QuestionSuggestions.vue # prompts guidés ("Comment était ma semaine ?")
│   │   ├── activity/
│   │   │   ├── ImportCsvDropzone.vue
│   │   │   └── ActivityListItem.vue
│   │   ├── ui/                          # boutons, dialogs, toasts, empty states
│   │   └── quota/
│   │       └── QuotaWidget.vue         # discret, en pied de page réglages
│   ├── composables/
│   │   ├── useApi.ts                    # wrapper mutation/query minimal (à la TanStack)
│   │   ├── useSse.ts                    # streaming chat
│   │   └── useIdempotency.ts            # génère UUID par requête sensible
│   ├── i18n/
│   │   └── fr.ts                        # V1 fr uniquement, prêt pour i18n
│   └── styles/
│       └── tailwind.css
└── tests/
```

## Stores Pinia — contrat

### `auth`
```ts
state: { user: User|null, plan: 'FREE'|'PRO', quotas: Quotas|null, accessToken, refreshToken }
actions: login, register, logout, refresh, fetchMe
```

### `prepa`
```ts
state: { active: Prepa|null, activities: Activity[], journal: JournalEntry[], reports: CoachReport[], planVersion: number }
actions: loadActive(), reload(), importCsv(file), weeklyUpdate() → poll job, setJournalEntry(week, content)
```

Le store `prepa` détient une seule prépa active en mémoire — pas de cache multi-prépa côté front (l'utilisateur switche via un dropdown qui recharge).

## Écrans clés — détail

### DashboardView (écran d'accueil)

Composition, top → bottom :

1. **Bandeau contextuel** : "Marathon de Paris — J-64" + barre de progression.
2. **Séance du jour** (grande carte) : type, distance, allures, description, boutons *Marquer faite* / *Marquer manquée* (raccourci sans passer par un import — utile hors Garmin).
3. **Cette semaine** : 7 mini-cartes jour par jour, statut visuel.
4. **Rapport coach le plus récent** (extrait, cliquable → détail).
5. **CTAs latéraux** (desktop) ou boutons flottants (mobile) : *Importer CSV*, *Poser une question*, *Faire le point*.

**Ce qu'on n'affiche PAS ici** : version du plan, allures en secondes, ID technique, quotas.

### PlanView

- Liste verticale de `WeekBlock`, "cette semaine" auto-scrollée en tête.
- Chaque semaine expand/collapse, affiche les séances avec `SeanceCard`.
- Filtres discrets : *Séances à venir uniquement* / *Toutes*.

### ChatView

- Historique de messages, composer en bas.
- **Suggestions de questions** au-dessus du composer (chips cliquables) : "Comment était ma semaine ?", "Puis-je encore viser 4h ?", "J'ai mal au mollet, je fais quoi ?" — ces suggestions **envoient une question canonique** (voir §05 sur le bénéfice tokens).
- Streaming SSE : les tokens s'affichent au fil.
- Indicateur discret "Basé sur ton plan actuel et tes 30 dernières séances" — l'utilisateur comprend que le coach a le contexte.
- Widget quota dans le coin (Free : "12/20 questions ce mois-ci").

### OnboardingWizard

Séquence guidée en ~7 étapes :
1. Course visée (autocomplete : marathons FR/EU connus, sinon saisie libre) + date.
2. Chrono visé (avec bouton "Aide-moi à choisir" qui pose 3 questions et propose).
3. Records récents (10 km, semi, plus longue sortie).
4. Disponibilités hebdo (jours + volume actuel).
5. Blessures / gênes.
6. Séances signature (optionnel).
7. Renforcement (opt-in).

À la fin : `POST /prepas` → `202 jobId` → écran d'attente animé pendant 15–30 s ("Ton coach construit ton plan…") → redirect Dashboard.

Chaque étape a un **retour arrière** et un **skip** raisonnable — l'onboarding ne doit pas bloquer sur un champ mal compris.

### ImportCsvDropzone

- Drag & drop OU click.
- Feedback immédiat : "42 séances détectées, 3 déjà connues, 39 importées". Preview d'un échantillon.
- Bouton "Faire analyser par mon coach" (déclenche weekly-update).

## Router & guards

```ts
{ path: '/', component: DashboardView, meta: { requiresAuth: true, requiresPrepa: true } },
{ path: '/onboarding', component: OnboardingWizard, meta: { requiresAuth: true } },
{ path: '/login', component: LoginView },
…
```

Guard :
```ts
router.beforeEach(async (to) => {
  if (to.meta.requiresAuth && !auth.user) return '/login';
  if (to.meta.requiresPrepa && !prepa.active) {
    await prepa.loadActive();
    if (!prepa.active) return '/onboarding';
  }
});
```

## Gestion des erreurs UI

- Interceptor Axios : sur `429 QUOTA_EXCEEDED` → modale non bloquante "Tu as atteint ta limite ce mois-ci, upgrade en Pro" avec CTA. **Pas de rejeu automatique.**
- Sur `502 LLM_UPSTREAM_ERROR` → toast "Ton coach est momentanément indisponible, réessaie dans quelques secondes." + bouton *Réessayer*.
- Sur `401` avec refresh possible → refresh silencieux et rejeu (1 fois max).

## Perf

- Lazy loading de toutes les routes (`() => import(...)`).
- Skeleton loaders sur Dashboard/Plan pendant le chargement initial.
- Cache local du plan avec clé `planVersion` — invalide seulement si le back renvoie une version supérieure.

## Accessibilité

- Composants Headless UI (a11y gratuit).
- Contrastes AA minimum.
- Navigation clavier complète sur le chat.
- Alt text sur les icônes signifiantes.

## Tests

- **Vitest** : stores, composables, utilitaires (formatage allure).
- **Vue Test Utils** : composants clés (`SeanceCard`, `ImportCsvDropzone`).
- **Playwright** (V1.1) : parcours E2E onboarding → import → chat.
