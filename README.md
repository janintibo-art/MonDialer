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

## Version 5.4 — Theme Studio

L'interface utilise maintenant sept palettes multicolores complètes :

- Océan Cyber — cyan, bleu électrique, magenta
- Synthwave — violet, rose laser, cyan
- Punk Toxique — vert acide, jaune, violet
- Éruption Solaire — orange, corail, or
- Candy Pulse — rose, cyan, violet
- Glace Écarlate — rouge, bleu glacier, indigo
- Or Royal — or, turquoise, pourpre

Chaque palette pilote le fond, les panneaux, le clavier, les halos, les bulles de messages, les boutons d'action et le bouton d'appel. Le sélecteur de thèmes affiche désormais de vraies cartes d'aperçu et met en évidence la palette active.
