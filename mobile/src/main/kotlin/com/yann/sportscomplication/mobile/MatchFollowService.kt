package com.yann.sportscomplication.mobile

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.wearable.Asset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Service de premier plan (foreground) qui continue d'interroger
 * TheSportsDB (toutes les 60s) ou Live Tennis API (tennis, toutes les
 * 3 min — voir pollIntervalMillis) selon la source du match suivi, et de
 * renvoyer les mises à jour à la montre MÊME quand l'app téléphone est
 * fermée ou balayée hors des apps récentes.
 *
 * Avant ce service, le polling tournait dans une coroutine liée au
 * cycle de vie de MainActivity (lifecycleScope) : il s'arrêtait dès que
 * l'utilisateur quittait l'app (voir README, section "Limites connues"
 * — désormais corrigée). Un Service de premier plan, avec sa
 * notification persistante (obligatoire sur Android 8+), n'est lui pas
 * soumis à ce cycle de vie : il continue de tourner tant qu'il n'a pas
 * explicitement été arrêté (match terminé, ou Yann appuie sur "Arrêter
 * le suivi").
 *
 * Il télécharge lui-même les logos des deux équipes au démarrage
 * (plutôt que de les recevoir de MainActivity) pour être totalement
 * autonome : si le processus téléphone redémarre pendant le suivi, ce
 * service peut repartir de zéro sans dépendre d'un état gardé en
 * mémoire par une Activity qui n'existe peut-être plus.
 *
 * NOTE Samsung : certains téléphones (dont les Galaxy) appliquent une
 * optimisation batterie agressive qui peut quand même arrêter ce
 * service. Si le suivi s'interrompt de façon inattendue, désactiver
 * l'optimisation de batterie pour cette app dans
 * Paramètres > Batterie > Sports Complication > Non optimisée.
 */
class MatchFollowService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollJob: Job? = null

    private var homeLogo: Asset? = null
    private var awayLogo: Asset? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopTracking()
            return START_NOT_STICKY
        }

        val match = intent?.toMatchResult()
        if (match == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        startTracking(match)
        // NOT_STICKY : si le système tue quand même le processus, on ne
        // veut pas qu'il relance ce service sans contexte de match — Yann
        // devra resélectionner un match depuis l'app. C'est un choix
        // délibéré plutôt que de complexifier avec une re-persistance de
        // l'Intent (START_REDELIVER_INTENT) pour un cas déjà rare grâce à
        // la notification de premier plan.
        return START_NOT_STICKY
    }

    private fun startTracking(initial: MatchResult) {
        pollJob?.cancel()
        startForeground(NOTIFICATION_ID, buildNotification(initial))

        pollJob = scope.launch {
            homeLogo = WatchSync.downloadLogo(initial.idHomeTeam)
            awayLogo = WatchSync.downloadLogo(initial.idAwayTeam)

            WatchSync.sendMatch(this@MatchFollowService, initial, homeLogo, awayLogo)
            FollowedMatchPrefs.save(this@MatchFollowService, initial)
            broadcastUpdate(initial)

            var current = initial
            while (isActive && !current.isFinished) {
                delay(pollIntervalMillis(current.source))
                val updated = try {
                    when (current.source) {
                        ApiSource.SPORTS_DB -> SportsDbApi.lookupEvent(current.id)
                        ApiSource.LIVE_TENNIS -> {
                            // Clé absente/vidée pendant le suivi (Yann l'a
                            // effacée dans l'app) : on saute ce cycle plutôt
                            // que de planter, comme une panne réseau —
                            // TennisApiKeyPrefs.kt.
                            val apiKey = TennisApiKeyPrefs.get(applicationContext)
                            if (apiKey == null) null else LiveTennisApi.lookupMatch(current.id, apiKey)
                        }
                    }
                } catch (e: Exception) {
                    null
                } ?: continue

                if (updated != current) {
                    WatchSync.sendMatch(this@MatchFollowService, updated, homeLogo, awayLogo)
                    FollowedMatchPrefs.save(this@MatchFollowService, updated)
                    updateNotification(updated)
                    broadcastUpdate(updated)
                    current = updated
                }
            }

            // Le match est terminé : plus rien à suivre, on s'arrête proprement.
            // CORRIGÉ (demandé par Yann le 15/09/2026) : sans ces deux lignes,
            // le repli Sofascore restait bloqué indéfiniment après une fin
            // NATURELLE d'un match suivi par l'API — FollowedMatchPrefs
            // n'était jusqu'ici vidé que sur arrêt MANUEL (bouton "Arrêter
            // le suivi", voir MainActivity.stopFollowing()), jamais quand
            // cette boucle s'arrêtait d'elle-même parce que le match était
            // fini. Résultat : refresh() de SofascoreNotificationListenerService
            // continuait à voir un FollowedMatchPrefs non-null (le match,
            // désormais fini) et à s'effacer devant lui pour toujours,
            // jusqu'à ce que Yann rouvre l'app et arrête le suivi à la main.
            FollowedMatchPrefs.clear(this@MatchFollowService)
            // Même raison que dans MainActivity.stopFollowing() : sans ça,
            // le repli Sofascore n'apparaîtrait qu'au prochain événement
            // reçu de Sofascore, pas immédiatement à la fin du suivi API.
            SofascoreNotificationListenerService.refreshIfConnected()
            stopForegroundAndSelf()
        }
    }

    /**
     * Le foot (TheSportsDB) n'a pas de plafond de requêtes par jour, juste
     * 30/min — 60s de battement est donc sans risque. Le tennis (Live
     * Tennis API, plan gratuit) plafonne lui à 100 requêtes/JOUR en plus
     * du 30/min : à 60s, un seul match de 2-3h épuiserait le quota du jour
     * à lui seul, recherches de joueurs comprises. 3 minutes laisse de la
     * marge (voir LiveTennisApi.kt et README).
     */
    private fun pollIntervalMillis(source: ApiSource): Long = when (source) {
        ApiSource.SPORTS_DB -> POLL_INTERVAL_MS_FOOTBALL
        ApiSource.LIVE_TENNIS -> POLL_INTERVAL_MS_TENNIS
    }

    private fun stopTracking() {
        pollJob?.cancel()
        stopForegroundAndSelf()
    }

    private fun stopForegroundAndSelf() {
        @Suppress("DEPRECATION")
        stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    /** Diffuse la mise à jour à MainActivity si elle est ouverte, pour rafraîchir la carte "match suivi" en direct. */
    private fun broadcastUpdate(match: MatchResult) {
        val intent = Intent(ACTION_MATCH_UPDATED).apply {
            setPackage(packageName)
            putExtras(match.toExtras())
        }
        sendBroadcast(intent)
    }

    private fun buildNotification(match: MatchResult): Notification {
        ensureChannel()
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Suivi de match en direct")
            .setContentText(match.title)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(match: MatchResult) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(match))
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Suivi de match",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Notification persistante pendant le suivi d'un match en direct"
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val POLL_INTERVAL_MS_FOOTBALL = 60_000L
        private const val POLL_INTERVAL_MS_TENNIS = 180_000L
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "match_follow"

        private const val ACTION_STOP = "com.yann.sportscomplication.mobile.action.STOP"
        const val ACTION_MATCH_UPDATED = "com.yann.sportscomplication.mobile.MATCH_UPDATED"

        /** Démarre (ou remplace) le suivi en arrière-plan du match donné. */
        fun start(context: Context, match: MatchResult) {
            val intent = Intent(context, MatchFollowService::class.java).putExtras(match.toExtras())
            ContextCompat.startForegroundService(context, intent)
        }

        /** Arrête le suivi en cours (bouton "Arrêter le suivi"). */
        fun stop(context: Context) {
            val intent = Intent(context, MatchFollowService::class.java).apply { action = ACTION_STOP }
            context.startService(intent)
        }
    }
}
