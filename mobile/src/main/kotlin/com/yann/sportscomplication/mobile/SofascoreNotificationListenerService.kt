package com.yann.sportscomplication.mobile

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Un groupe de notifications Sofascore actif = un match ("un groupe de
 * notifications correspond à un match", Yann) — [groupKey] est la clé
 * système ([StatusBarNotification.getGroupKey]) qui les relie, stable tant
 * que le groupe reste actif. [latestLine] est la ligne la plus récente,
 * pour donner un aperçu dans le sélecteur (MainActivity).
 */
data class SofascoreMatchOption(
    val groupKey: String,
    val homeTeam: String,
    val awayTeam: String,
    val latestLine: String
)

/**
 * Repli "pas de match sélectionné" (voir README) : relit les notifications
 * de l'app Sofascore (`com.sofascore.results`, vérifié via Play Store — pas
 * à confondre avec les apps "Livesport"/Soccerway, éditeur différent) et
 * pousse le score déduit vers la montre via [WatchSync], SEULEMENT si aucun
 * match n'est suivi manuellement (voir [FollowedMatchPrefs] — c'est
 * MainActivity/MatchFollowService qui priment sinon).
 *
 * Deux modes, choisis par Yann dans l'app et persistés dans [SofascorePrefs] :
 * - LATEST (par défaut) : le groupe le plus récemment mis à jour parmi
 *   toutes les notifs Sofascore actives.
 * - CHOSEN : un match précis, choisi à la main parmi une liste de ceux
 *   actuellement dans le centre de notifications (voir [listAvailableMatches],
 *   appelé depuis MainActivity). Si ce match n'a plus de notif active (fini,
 *   groupe supprimé), on retombe automatiquement sur LATEST plutôt que de ne
 *   rien afficher.
 *
 * Nécessite que Yann accorde l'accès aux notifications à cette app
 * (permission spéciale, non demandable au runtime contrairement à
 * POST_NOTIFICATIONS — voir le bouton dédié dans MainActivity qui ouvre
 * directement l'écran système).
 *
 * CONFIRMÉ SUR APPAREIL (test du 12/09, Real Madrid - Rayo Vallecano) :
 * Sofascore poste UNE SEULE notification par match, mise à jour en place,
 * en style Inbox (`EXTRA_TEXT_LINES`, plafonné à 6 lignes par Android —
 * les 6 lignes de la capture le confirment). [collectLines] gère aussi le
 * cas où Sofascore posterait plutôt une notification par événement
 * regroupée par le système (`StatusBarNotification.getGroupKey`), mais ce
 * n'est pas le cas observé.
 *
 * ATTENTION ORDRE : `InboxStyle.addLine()` affiche les lignes dans leur
 * ordre d'ajout, la première ajoutée en haut (doc officielle Android). La
 * capture montre l'événement le plus récent EN HAUT ("Match terminé"),
 * donc Sofascore ajoute chaque nouvel événement EN PREMIER (`addLine`
 * appelé avec la dernière ligne avant les anciennes) : le tableau brut
 * `EXTRA_TEXT_LINES` est donc déjà trié du plus récent au plus ancien.
 * [collectLines] ne doit PAS le renverser (une version antérieure le
 * renversait par erreur, ce qui faisait remonter le PREMIER but marqué
 * dans le match — ex. 1-0 — au lieu du score final, la boucle de
 * [SofascoreNotificationParser.parse] s'arrêtant à la première ligne
 * reconnue).
 */
class SofascoreNotificationListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        refresh()
    }

    override fun onListenerDisconnected() {
        instance = null
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == SOFASCORE_PACKAGE) refresh()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName == SOFASCORE_PACKAGE) refresh()
    }

    /** Relit les notifications actives et pousse le repli à la montre, s'il y a lieu. */
    fun refresh() {
        // Le suivi manuel (recherche dans l'app) prime toujours sur ce repli.
        if (FollowedMatchPrefs.load(this) != null) return

        val groups = activeSofascoreGroups() ?: return
        if (groups.isEmpty()) return

        // Si un match précis a été choisi ET qu'il a encore une notif
        // active, on le suit ; sinon (mode "dernière", ou match choisi
        // terminé/supprimé) on retombe sur le groupe le plus récent.
        val chosenKey = SofascorePrefs.loadChosenGroupKey(this)
            ?.takeIf { SofascorePrefs.loadMode(this) == SofascorePrefs.Mode.CHOSEN }
        val targetGroup = (chosenKey?.let { groups[it] })
            ?: groups.values.maxByOrNull { group -> group.maxOf { it.postTime } }
            ?: return

        val (homeTeam, awayTeam) = extractTeams(targetGroup) ?: return
        val lines = collectLines(targetGroup)
        if (lines.isEmpty()) return

        val match = SofascoreNotificationParser.parse(homeTeam, awayTeam, lines)
            ?: buildRawFallback(homeTeam, awayTeam, lines.first())

        WatchSync.sendMatch(this, match, homeLogo = null, awayLogo = null)
    }

    /**
     * Liste les matchs Sofascore actuellement dans le centre de
     * notifications, pour le sélecteur de MainActivity — un par groupe
     * actif. Vide si l'accès aux notifications n'est pas accordé, ou si
     * aucune notif Sofascore n'est active.
     */
    fun listAvailableMatches(): List<SofascoreMatchOption> {
        val groups = activeSofascoreGroups() ?: return emptyList()
        return groups.mapNotNull { (groupKey, group) ->
            val (homeTeam, awayTeam) = extractTeams(group) ?: return@mapNotNull null
            val latestLine = collectLines(group).firstOrNull().orEmpty()
            SofascoreMatchOption(groupKey, homeTeam, awayTeam, latestLine)
        }
    }

    /** Notifs Sofascore actives, groupées par match — null si l'accès aux notifications n'est pas accordé. */
    private fun activeSofascoreGroups(): Map<String, List<StatusBarNotification>>? = try {
        activeNotifications.filter { it.packageName == SOFASCORE_PACKAGE }.groupBy { it.groupKey }
    } catch (e: Exception) {
        null
    }

    /**
     * "Real Madrid - Rayo Vallecano" -> domicile/extérieur. Split sur
     * " - " (espaces des deux côtés) et non sur tout tiret, pour ne pas
     * couper un nom d'équipe composé (ex. "Saint-Germain", sans espaces
     * autour de son tiret).
     */
    private fun extractTeams(group: List<StatusBarNotification>): Pair<String, String>? {
        val title = group.firstNotNullOfOrNull { sbn ->
            sbn.notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
        } ?: return null
        val teams = title.split(" - ").map { it.trim() }
        if (teams.size != 2 || teams.any { it.isEmpty() }) return null
        return teams[0] to teams[1]
    }

    /**
     * Récupère les lignes du groupe, DU PLUS RÉCENT AU PLUS ANCIEN — que
     * Sofascore poste une notification par événement (chaque
     * [StatusBarNotification] du groupe = une ligne, on trie alors par
     * date de publication) ou une seule notif mise à jour en place
     * (`EXTRA_TEXT_LINES`, style Inbox). Dans ce second cas — celui
     * confirmé sur appareil — le tableau `EXTRA_TEXT_LINES` est DÉJÀ
     * trié du plus récent au plus ancien (voir la note en tête de
     * fichier) : on ne le renverse plus.
     */
    private fun collectLines(group: List<StatusBarNotification>): List<String> {
        val lines = mutableListOf<String>()
        for (sbn in group.sortedByDescending { it.postTime }) {
            val extras = sbn.notification.extras
            val textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            if (textLines != null && textLines.isNotEmpty()) {
                lines += textLines.map { it.toString() }
            } else {
                extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.let { lines += it }
            }
        }
        return lines
    }

    /**
     * Repli neutre pour tout ce qu'on ne sait pas encore parser (tennis,
     * événement foot pas encore couvert) : affiche le texte brut de la
     * notif la plus récente tel quel, sans essayer d'en déduire un score —
     * pour ne jamais afficher une donnée fausse. `homeScore`/`awayScore`
     * restent null, donc MatchResult.title retombe sur "Équipe vs Équipe"
     * et wear/MatchClock.kt affiche [rawLine] tel quel comme statut (aucune
     * des branches connues de MatchClock ne le reconnaît).
     */
    private fun buildRawFallback(homeTeam: String, awayTeam: String, rawLine: String) = MatchResult(
        id = "sofascore_fallback_raw",
        source = ApiSource.SPORTS_DB,
        idHomeTeam = null,
        idAwayTeam = null,
        homeTeam = homeTeam,
        awayTeam = awayTeam,
        homeScore = null,
        awayScore = null,
        date = SportsDbApi.todayUtcDateString(),
        time = null,
        status = rawLine,
        league = "Sofascore",
        kickoffEpochMillis = null
    )

    companion object {
        // Vérifié via la fiche Play Store de Sofascore (play.google.com,
        // id=com.sofascore.results) — à ne pas confondre avec
        // "eu.livesport.*", éditeur différent (Livesport s.r.o., Soccerway).
        private const val SOFASCORE_PACKAGE = "com.sofascore.results"

        private var instance: SofascoreNotificationListenerService? = null

        /**
         * Appelé quand le suivi manuel s'arrête (MainActivity.stopFollowing)
         * ou que Yann change son choix de repli, pour réafficher
         * immédiatement le résultat sans attendre le prochain événement
         * Sofascore. Sans effet si le service n'est pas encore connecté
         * (accès aux notifications pas encore accordé).
         */
        fun refreshIfConnected() {
            instance?.refresh()
        }

        /** Voir [SofascoreNotificationListenerService.listAvailableMatches] — liste vide si le service n'est pas connecté. */
        fun listAvailableMatchesIfConnected(): List<SofascoreMatchOption> =
            instance?.listAvailableMatches() ?: emptyList()
    }
}
