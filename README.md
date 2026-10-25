# NoScroll

App Android personnelle (Kotlin, ViewBinding) qui bloque les Reels Instagram et les Shorts YouTube
via un service d'accessibilité. Package : `com.aftra.noscroll`, minSdk 26.

## Construire et installer l'APK
1. Android Studio : ouvrir le dossier, laisser Gradle se synchroniser.
2. Build → Build APK(s), ou en ligne de commande : `./gradlew assembleDebug`.
3. L'APK est dans `app/build/outputs/apk/debug/app-debug.apk`. L'installer sur le téléphone
   (autoriser les sources inconnues, ou `adb install -r app-debug.apk`).
4. Ouvrir NoScroll → « Ouvrir les réglages d'accessibilité » → activer NoScroll.
   Si Android grise l'option (installation hors Play Store) : Infos de l'app → ⋮ → « Autoriser les paramètres restreints ».

## Utilisation
- Deux interrupteurs : Reels / Shorts.
- Pause de 15 min depuis l'app ou la tuile « NoScroll » des réglages rapides (à ajouter via l'édition des tuiles).
- Compteur de blocages du jour et total sur l'écran principal.

## Quand une app change son interface
Tous les identifiants sont dans `app/src/main/java/com/aftra/noscroll/Detectors.kt`.
1. Passer `DUMP_TREE` à `true`, relancer, ouvrir un Reel/Short.
2. Logcat, filtre `NoScroll` : repérer les `id=` propres au lecteur et à l'onglet.
3. Mettre à jour `contentIds`, `tabIds`, `tabLabels`, `homeTabIds`, `homeTabLabels`.

## Fiabilité
- Score de détection combiné (id du lecteur et onglet « selected » = 2, simple mot = 1, seuil = 2).
- Cooldown 1 s entre blocages, pas de blocage écran éteint, anti-boucle (4 blocages en 5 s ⇒ pause 10 s).
