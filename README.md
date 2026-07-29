# Mon Dialer

Application Téléphone par défaut pour Android (min. Android 10) avec filtrage anti-spam :

- **Numéros bloqués** : liste personnelle, ajout/suppression (appui long pour supprimer).
- **Préfixes bloqués** : ex. `01` pour bloquer Paris / Île-de-France (`+331` et `00331` sont automatiquement normalisés en `01`).
- **Liste ARCEP intégrée** : préfixes réservés au démarchage commercial en France (0162, 0163, 0270, 0271, 0377, 0378, 0424, 0425, 0568, 0569, 0948, 0949), activable/désactivable.
- **Numéros masqués** : blocage optionnel.
- **Numéros "voisins"** : bloque les numéros qui ressemblent anormalement au vôtre (mêmes 6 premiers chiffres, même longueur) — technique du *neighbor spoofing*.

## Compilation

Le dépôt contient un workflow GitHub Actions : chaque `git push` sur `main` compile automatiquement l'APK.
L'APK est récupérable dans l'onglet **Actions → dernier run → Artifacts → MonDialer-debug-apk**.

## Installation

1. Installer l'APK (autoriser les sources inconnues).
2. Ouvrir l'app → **« Définir comme app Téléphone »** et accepter.
3. Ouvrir **« Filtres anti-spam »** et configurer.

En tant qu'app Téléphone par défaut, son service de filtrage (`CallScreeningService`) est utilisé par Android pour rejeter silencieusement les appels correspondant aux règles.
