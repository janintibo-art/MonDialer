# Mon Dialer v2

Application Téléphone par défaut pour Android (min. Android 10) — design futuriste néon.

## Fonctionnalités
- **Clavier d'appel** au design néon cyan sur fond sombre.
- **Contacts** : lecture directe du carnet système → toujours synchronisé avec l'app Contacts actuelle (et Google).
- **Journal d'appels** : lecture directe de l'historique système (entrants ↙, sortants ↗, manqués ✕, rejetés ⊘, bloqués ⛔). Un appui sur une ligne recompose le numéro.
- **Anti-spam** :
  - numéros bloqués (liste personnelle),
  - préfixes bloqués (ex. `01` = Paris/IDF ; `+331`/`00331` normalisés),
  - liste ARCEP intégrée des préfixes de démarchage,
  - numéros masqués,
  - numéros « voisins » ressemblant anormalement au vôtre (neighbor spoofing).

## Compilation
Push sur `main` → GitHub Actions compile l'APK → onglet **Actions → Artifacts → MonDialer-debug-apk**.
