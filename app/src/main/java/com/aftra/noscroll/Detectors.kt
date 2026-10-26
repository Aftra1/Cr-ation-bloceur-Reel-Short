package com.aftra.noscroll

/**
 * TOUS les identifiants liés aux apps ciblées sont ici. Quand Instagram ou YouTube
 * changent leur interface, c'est le seul fichier à mettre à jour.
 *
 * Les ids sont écrits SANS le préfixe "<package>:id/". Les libellés sont en minuscules.
 * Valeurs de départ écrites de mémoire : à vérifier avec Logcat (filtre "NoScroll"),
 * en passant DUMP_TREE à true pour voir tous les ids de l'écran courant.
 */
object Detectors {

    const val TAG = "NoScroll"

    /** Logue l'arbre complet à chaque changement d'état (verbeux, pour trouver de nouveaux ids). */
    const val DUMP_TREE = false

    /** Score minimal pour considérer qu'on est sur Reels/Shorts. Fort = 2, faible = 1. */
    const val SCORE_THRESHOLD = 2
    const val SCORE_STRONG = 2
    const val SCORE_WEAK = 1

    /** Garde-fous de parcours de l'arbre. */
    const val MAX_DEPTH = 40
    const val MAX_NODES = 1500

    class Spec(
        val packageName: String,
        val kind: Kind,
        /** Vues présentes uniquement dans le lecteur Reels/Shorts (signal fort). */
        val contentIds: Set<String>,
        /** Ids de l'onglet Reels/Shorts de la barre du bas (fort s'il est "selected"). */
        val tabIds: Set<String>,
        /** Libellés (text ou contentDescription) de cet onglet (fort s'il est "selected"). */
        val tabLabels: Set<String>,
        /** Mots qui évoquent Reels/Shorts mais apparaissent aussi ailleurs (signal faible). */
        val weakWords: Set<String>,
        /** Onglet Accueil, pour le blocage (étape 4). */
        val homeTabIds: Set<String>,
        val homeTabLabels: Set<String>,
    )

    enum class Kind { REELS, SHORTS }

    val INSTAGRAM = Spec(
        packageName = "com.instagram.android",
        kind = Kind.REELS,
        contentIds = setOf(
            "clips_viewer_view_pager",
            "clips_viewer_container",
            "clips_video_container",
            "clips_swipe_refresh_container",
            "root_clips_layout",
        ),
        tabIds = setOf("clips_tab"),
        tabLabels = setOf("reels"),
        weakWords = setOf("reels"),
        homeTabIds = setOf("feed_tab"),
        homeTabLabels = setOf("accueil", "home"),
    )

    val YOUTUBE = Spec(
        packageName = "com.google.android.youtube",
        kind = Kind.SHORTS,
        contentIds = setOf(
            "reel_recycler",
            "reel_player_page_container",
            "reel_watch_fragment_root",
            "shorts_container",
            "reel_time_bar",
        ),
        tabIds = setOf("shorts_pivot_item"),
        tabLabels = setOf("shorts"),
        weakWords = setOf("shorts"),
        homeTabIds = setOf("home_pivot_item"),
        homeTabLabels = setOf("accueil", "home"),
    )

    val ALL = listOf(INSTAGRAM, YOUTUBE)

    fun specFor(packageName: CharSequence?): Spec? =
        ALL.firstOrNull { it.packageName == packageName?.toString() }
}
