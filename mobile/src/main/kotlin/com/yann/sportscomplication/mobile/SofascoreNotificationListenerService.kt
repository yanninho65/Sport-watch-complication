package com.yann.sportscomplication.mobile

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Une notification Sofascore active = un match. [key] est
 * [StatusBarNotification.getKey], stable tant que la notif reste active (une
 * mise à jour en place — même id/tag — garde la même clé). [latestLine] est
 * la ligne la plus récente, pour donner un aperçu dans le sélecteur
 * (MainActivity).
 */
data class SofascoreMatchOption(
    val key: String,
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
 * - LATEST (par défaut) : la notif la plus récemment mise à jour parmi
 *   toutes les notifs Sofascore actives.
 * - CHOSEN : un match précis, choisi à la main parmi une liste de ceux
 *   actuellement dans le centre de notifications (voir [listAvailableMatches],
 *   appelé depuis MainActivity). Si ce match n'a plus de notif active (fini,
 *   notif supprimée), on retombe automatiquement sur LATEST plutôt que de ne
 *   rien afficher.
 *
 * Nécessite que Yann accorde l'accès aux notifications à cette app
 * (permission spéciale, non demandable au runtime contrairement à
 * POST_NOTIFICATIONS — voir le bouton dédié dans MainActivity qui ouvre
 * directement l'écran système).
 *
 * CONFIRMÉ SUR APPAREIL (test du 12/09, Real Madrid - Rayo Vallecano) :
 * Sofascore poste UNE notification par match, mise à jour en place, en
 * style Inbox (`EXTRA_TEXT_LINES`, plafonné à 6 lignes par Android — les 6
 * lignes de la capture le confirment).
 *
 * ATTENTION ORDRE : `InboxStyle.addLine()` affiche les lignes dans leur
 * ordre d'ajout, la première ajoutée en haut (doc officielle Android). La
 * capture montre l'événement le plus récent EN HAUT ("Match terminé"),
 * donc Sofascore ajoute chaque nouvel événement EN PREMIER : le tableau brut
 * `EXTRA_TEXT_LINES` est donc déjà trié du plus récent au plus ancien —
 * [collectLines] ne le renverse pas.
 *
 * CORRIGÉ (test du 14/09, notif tennis affichant le score du foot terminé) :
 * une version antérieure regroupait les notifs par
 * [StatusBarNotification.getGroupKey] en supposant qu'une clé de groupe =
 * un match. En réalité Sofascore semble regrouper TOUTES ses notifications
 * (tous matchs confondus) sous la même clé de groupe système — un match en
 * cours de foot et un match de tennis qui démarre se retrouvaient donc dans
 * le MÊME groupe, et [extractTeams]/[collectLines] picoraient des lignes
 * des deux matchs mélangées (d'où le score du foot terminé qui ressortait
 * sur la notif tennis). Le code ne groupe plus du tout : chaque
 * [StatusBarNotification] Sofascore active est traitée individuellement
 * (une notif = un match, mise à jour en place — voir plus haut), identifiée
 * par sa propre [StatusBarNotification.getKey] plutôt que par un groupKey
 * partagé.
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

        val notifications = activeSofascoreNotifications() ?: return
        if (notifications.isEmpty()) return

        // Si un match précis a été choisi ET qu'il a encore une notif
        // active, on le suit ; sinon (mode "dernière", ou match choisi
        // terminé/supprimé) on retombe sur la notif la plus récente.
        val chosenKey = SofascorePrefs.loadChosenKey(this)
            ?.takeIf { SofascorePrefs.loadMode(this) == SofascorePrefs.Mode.CHOSEN }
        val target = (chosenKey?.let { key -> notifications.find { it.key == key } })
            ?: notifications.maxByOrNull { it.postTime }
            ?: return

        val (homeTeam, awayTeam) = extractTeams(target) ?: return
        val lines = collectLines(target)
        if (lines.isEmpty()) return

        val match = SofascoreNotificationParser.parse(homeTeam, awayTeam, lines)
            ?: buildRawFallback(homeTeam, awayTeam, lines.first())

        WatchSync.sendMatch(this, match, homeLogo = null, awayLogo = null)
    }

    /**
     * Liste les matchs Sofascore actuellement dans le centre de
     * notifications, pour le sélecteur de MainActivity — un par notif
     * active. Vide si l'accès aux notifications n'est pas accordé, ou si
     * aucune notif Sofascore n'est active.
     */
    fun listAvailableMatches(): List<SofascoreMatchOption> {
        val notifications = activeSofascoreNotifications() ?: return emptyList()
        return notifications.mapNotNull { sbn ->
            val (homeTeam, awayTeam) = extractTeams(sbn) ?: return@mapNotNull null
            val latestLine = collectLines(sbn).firstOrNull().orEmpty()
            SofascoreMatchOption(sbn.key, homeTeam, awayTeam, latestLine)
        }
    }

    /** Notifs Sofascore actives, une par match — null si l'accès aux notifications n'est pas accordé. */
    private fun activeSofascoreNotifications(): List<StatusBarNotification>? = try {
        activeNotifications.filter { it.packageName == SOFASCORE_PACKAGE }
    } catch (e: Exception) {
        null
    }

    /**
     * "Real Madrid - Rayo Vallecano" -> domicile/extérieur. Split sur
     * " - " (espaces des deux côtés) et non sur tout tiret, pour ne pas
     * couper un nom d'équipe composé (ex. "Saint-Germain", sans espaces
     * autour de son tiret).
     */
    private fun extractTeams(sbn: StatusBarNotification): Pair<String, String>? {
        val title = sbn.notification.extras
            .getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
            ?: return null
        val teams = title.split(" - ").map { it.trim() }
        if (teams.size != 2 || teams.any { it.isEmpty() }) return null
        return teams[0] to teams[1]
    }

    /**
     * Récupère les lignes d'UNE notif, DU PLUS RÉCENT AU PLUS ANCIEN.
     * Cas confirmé sur appareil (voir note en tête de fichier) : notif
     * unique mise à jour en place, `EXTRA_TEXT_LINES` déjà trié du plus
     * récent au plus ancien (pas besoin de le renverser). Repli sur
     * `EXTRA_TEXT` (une seule ligne) si `EXTRA_TEXT_LINES` est absent.
     */
    private fun collectLines(sbn: StatusBarNotification): List<String> {
        val extras = sbn.notification.extras
        val textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
        if (textLines != null && textLines.isNotEmpty()) {
            return textLines.map { it.toString() }
        }
        return extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?.let { listOf(it) }
            ?: emptyList()
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
