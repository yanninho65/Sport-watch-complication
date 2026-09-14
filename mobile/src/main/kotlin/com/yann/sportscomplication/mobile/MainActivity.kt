package com.yann.sportscomplication.mobile

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.yann.sportscomplication.mobile.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

/**
 * Demande d'abord QUELLE API interroger (TheSportsDB ou Live Tennis
 * API — voir [ApiMode]), puis QUEL SPORT dans cette API (plusieurs
 * sports possibles pour TheSportsDB, voir [SportsDbSport] ; un seul
 * pour Live Tennis API, implicite), puis recherche un match dans ce
 * sport (par équipe, joueur ou ligue pour TheSportsDB ; par joueur
 * uniquement en tennis) et lance son suivi.
 *
 * Le polling périodique et l'envoi à la montre ne vivent plus ici depuis
 * l'introduction de MatchFollowService (foreground service, continue
 * même app fermée) : cette Activity se contente de (1) faire les
 * recherches, (2) démarrer/arrêter le service, et (3) refléter l'état du
 * suivi en cours (carte du haut), via FollowedMatchPrefs au démarrage
 * puis via un broadcast pendant qu'elle est ouverte.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    /** Quelle API interroger — voir LiveTennisApi.kt et SportsDbApi.kt. */
    private enum class ApiMode { SPORTS_DB, LIVE_TENNIS }
    private var apiMode = ApiMode.SPORTS_DB

    /**
     * Sport interrogé DANS TheSportsDB (qui couvre plusieurs sports, pas
     * que le foot) — sert à filtrer les recherches côté client via le
     * champ `strSport` de l'API (voir SportsDbApi.searchTeams/
     * searchPlayers/searchLeagues). N'a pas de sens pour Live Tennis API
     * (mono-sport, tennis implicite). [apiValue] est le libellé exact
     * attendu par TheSportsDB pour ce sport.
     */
    private enum class SportsDbSport(val label: String, val apiValue: String) {
        SOCCER("Football", "Soccer"),
        BASKETBALL("Basketball", "Basketball"),
        BASEBALL("Baseball", "Baseball"),
        ICE_HOCKEY("Hockey sur glace", "Ice Hockey"),
        AMERICAN_FOOTBALL("Football US", "American Football")
    }
    private var sportsDbSport = SportsDbSport.SOCCER

    private enum class SearchMode { TEAM, PLAYER, LEAGUE }
    private var searchMode = SearchMode.TEAM

    /** Reçoit les mises à jour envoyées par MatchFollowService pendant que l'app est ouverte au premier plan. */
    private val matchUpdatedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            intent.toMatchResult()?.let { showFollowedCard(it) }
        }
    }

    // Le refus de cette permission n'empêche pas le suivi de fonctionner :
    // seule la notification persistante du service resterait invisible
    // (comportement standard Android 13+, voir doc POST_NOTIFICATIONS).
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.recyclerView.layoutManager = LinearLayoutManager(this)

        binding.buttonSearch.setOnClickListener { runSearch() }
        binding.editSearch.setOnEditorActionListener { _, _, _ ->
            runSearch()
            true
        }
        binding.buttonBackToSearch.setOnClickListener { showSearchState() }
        binding.buttonStopFollowing.setOnClickListener { stopFollowing() }

        // Question 1 : quelle API. Repart d'un état de recherche propre à
        // chaque changement — le sélecteur de sport TheSportsDB et le
        // sélecteur équipe/joueur/ligue n'ont pas de sens en tennis, et
        // une liste de résultats de l'API précédente resterait affichée
        // sinon.
        binding.radioApi.setOnCheckedChangeListener { _, checkedId ->
            apiMode = if (checkedId == R.id.radioApiTennis) ApiMode.LIVE_TENNIS else ApiMode.SPORTS_DB
            showSearchState()
        }

        // Question 2 (TheSportsDB uniquement) : quel sport.
        binding.radioSportsDbSport.setOnCheckedChangeListener { _, checkedId ->
            sportsDbSport = when (checkedId) {
                R.id.radioSdbBasketball -> SportsDbSport.BASKETBALL
                R.id.radioSdbBaseball -> SportsDbSport.BASEBALL
                R.id.radioSdbIceHockey -> SportsDbSport.ICE_HOCKEY
                R.id.radioSdbAmFootball -> SportsDbSport.AMERICAN_FOOTBALL
                else -> SportsDbSport.SOCCER
            }
            showSearchState()
        }

        binding.radioSearchMode.setOnCheckedChangeListener { _, checkedId ->
            searchMode = when (checkedId) {
                R.id.radioModePlayer -> SearchMode.PLAYER
                R.id.radioModeLeague -> SearchMode.LEAGUE
                else -> SearchMode.TEAM
            }
            binding.editSearch.hint = when (searchMode) {
                SearchMode.TEAM -> "Nom d'équipe (ex. PSG)"
                SearchMode.PLAYER -> "Nom de joueur (ex. Mbappé)"
                SearchMode.LEAGUE -> "Nom de ligue (ex. Ligue 1)"
            }
        }

        // Clé Live Tennis API saisie ICI, jamais dans le code (dépôt
        // GitHub public) — voir TennisApiKeyPrefs.kt.
        binding.buttonSaveTennisApiKey.setOnClickListener {
            val key = binding.editTennisApiKey.text?.toString()?.trim().orEmpty()
            if (key.isEmpty()) {
                Toast.makeText(this, "Colle d'abord ta clé", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            TennisApiKeyPrefs.save(this, key)
            Toast.makeText(this, "Clé enregistrée", Toast.LENGTH_SHORT).show()
        }

        showSearchState()
        requestNotificationPermissionIfNeeded()
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            this,
            matchUpdatedReceiver,
            IntentFilter(MatchFollowService.ACTION_MATCH_UPDATED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        val followed = FollowedMatchPrefs.load(this)
        if (followed != null) showFollowedCard(followed) else hideFollowedCard()
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(matchUpdatedReceiver)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun runSearch() {
        val query = binding.editSearch.text?.toString()?.trim().orEmpty()
        if (query.isEmpty()) return

        binding.textStatus.text = "Recherche de \"$query\"…"

        when (apiMode) {
            ApiMode.LIVE_TENNIS -> searchTennisPlayers(query)
            ApiMode.SPORTS_DB -> when (searchMode) {
                SearchMode.TEAM -> searchTeams(query)
                SearchMode.PLAYER -> searchPlayers(query)
                SearchMode.LEAGUE -> searchLeagues(query)
            }
        }
    }

    /** Tennis : la recherche se fait uniquement par nom de joueur (pas d'équipe/ligue au sens de TheSportsDB). */
    private fun searchTennisPlayers(query: String) {
        val apiKey = TennisApiKeyPrefs.get(this)
        if (apiKey == null) {
            binding.textStatus.text = "Renseigne ta clé Live Tennis API ci-dessus avant de chercher"
            return
        }
        lifecycleScope.launch {
            val players = safeCall { LiveTennisApi.searchPlayers(query, apiKey) }
            if (players.isEmpty()) {
                binding.textStatus.text = "Aucun joueur trouvé pour \"$query\""
                binding.recyclerView.adapter = null
                return@launch
            }
            binding.textStatus.text = "${players.size} joueur(s) trouvé(s) — choisis-en un"
            binding.recyclerView.adapter = SimpleListAdapter(players, ::tennisPlayerLabel) { player ->
                showTennisMatchesForPlayerToday(player.id, player.name, apiKey)
            }
        }
    }

    private fun tennisPlayerLabel(player: TennisPlayerResult): String {
        val ranking = player.ranking?.let { " · #$it" } ?: ""
        val tour = player.tour?.let { " · $it" } ?: ""
        return "${player.name}$ranking$tour"
    }

    private fun showTennisMatchesForPlayerToday(playerId: Int, label: String, apiKey: String) {
        binding.textStatus.text = "Chargement des matchs de $label…"
        lifecycleScope.launch {
            val matches = safeCall { LiveTennisApi.getMatchesForPlayerToday(playerId, apiKey) }
            showMatchResults(matches, label)
        }
    }

    private fun searchTeams(query: String) {
        lifecycleScope.launch {
            val teams = safeCall { SportsDbApi.searchTeams(query, sportsDbSport.apiValue) }
            if (teams.isEmpty()) {
                binding.textStatus.text = "Aucune équipe trouvée pour \"$query\" (${sportsDbSport.label})"
                binding.recyclerView.adapter = null
                return@launch
            }
            binding.textStatus.text = "${teams.size} équipe(s) trouvée(s) — choisis-en une"
            binding.recyclerView.adapter = SimpleListAdapter(teams, { it.name }) { team ->
                showMatchesForTeamToday(team.id, team.name)
            }
        }
    }

    private fun searchPlayers(query: String) {
        lifecycleScope.launch {
            val players = safeCall { SportsDbApi.searchPlayers(query, sportsDbSport.apiValue) }
            if (players.isEmpty()) {
                binding.textStatus.text = "Aucun joueur trouvé pour \"$query\" (${sportsDbSport.label})"
                binding.recyclerView.adapter = null
                return@launch
            }
            binding.textStatus.text = "${players.size} joueur(s) trouvé(s) — choisis-en un"
            binding.recyclerView.adapter = SimpleListAdapter(
                players,
                { "${it.name} (${it.teamName ?: "équipe inconnue"})" }
            ) { player ->
                val teamId = player.teamId
                if (teamId == null) {
                    Toast.makeText(this@MainActivity, "Équipe inconnue pour ce joueur", Toast.LENGTH_SHORT).show()
                } else {
                    showMatchesForTeamToday(teamId, player.teamName ?: player.name)
                }
            }
        }
    }

    private fun searchLeagues(query: String) {
        lifecycleScope.launch {
            val leagues = safeCall { SportsDbApi.searchLeagues(query, sportsDbSport.apiValue) }
            if (leagues.isEmpty()) {
                binding.textStatus.text = "Aucune ligue trouvée pour \"$query\" (${sportsDbSport.label})"
                binding.recyclerView.adapter = null
                return@launch
            }
            binding.textStatus.text = "${leagues.size} ligue(s) trouvée(s) — choisis-en une"
            binding.recyclerView.adapter = SimpleListAdapter(leagues, { it.name }) { league ->
                showMatchesForLeagueToday(league.id, league.name)
            }
        }
    }

    private fun showMatchesForTeamToday(teamId: String, label: String) {
        binding.textStatus.text = "Chargement des matchs de $label…"
        lifecycleScope.launch {
            // getMatchesForTeam renvoie les derniers/prochains matchs (pas
            // forcément aujourd'hui) — filtrage côté client nécessaire ici,
            // contrairement à showMatchesForLeagueToday où eventsday.php
            // filtre déjà par date côté serveur.
            val matches = safeCall { SportsDbApi.getMatchesForTeam(teamId) }
                .filter { SportsDbApi.isToday(it.date) }
            showMatchResults(matches, label)
        }
    }

    private fun showMatchesForLeagueToday(leagueId: String, label: String) {
        binding.textStatus.text = "Chargement des matchs de $label…"
        lifecycleScope.launch {
            val matches = safeCall { SportsDbApi.getMatchesForLeagueToday(leagueId) }
            showMatchResults(matches, label)
        }
    }

    private fun showMatchResults(matches: List<MatchResult>, label: String) {
        binding.buttonBackToSearch.visibility = View.VISIBLE
        if (matches.isEmpty()) {
            binding.textStatus.text = "Aucun match aujourd'hui pour $label"
            binding.recyclerView.adapter = null
            return
        }
        binding.textStatus.text = "Matchs aujourd'hui — $label"
        binding.recyclerView.adapter = MatchesAdapter(matches) { match -> onMatchSelected(match) }
    }

    private suspend fun <T> safeCall(block: suspend () -> List<T>): List<T> = try {
        block()
    } catch (e: Exception) {
        Toast.makeText(this@MainActivity, "Erreur réseau", Toast.LENGTH_SHORT).show()
        emptyList()
    }

    private fun onMatchSelected(match: MatchResult) {
        FollowedMatchPrefs.save(this, match)
        MatchFollowService.start(this, match)
        Toast.makeText(this, "Suivi démarré : ${match.title}", Toast.LENGTH_SHORT).show()
        showFollowedCard(match)
    }

    private fun stopFollowing() {
        MatchFollowService.stop(this)
        WatchSync.sendCleared(this)
        FollowedMatchPrefs.clear(this)
        hideFollowedCard()
    }

    private fun showFollowedCard(match: MatchResult) {
        binding.followedMatchCard.visibility = View.VISIBLE
        binding.textFollowedTitle.text = match.title
        binding.textFollowedDetails.text = match.details
    }

    private fun hideFollowedCard() {
        binding.followedMatchCard.visibility = View.GONE
    }

    private fun showSearchState() {
        binding.buttonBackToSearch.visibility = View.GONE
        binding.recyclerView.adapter = null
        binding.editSearch.setText("")

        when (apiMode) {
            ApiMode.SPORTS_DB -> {
                binding.radioSportsDbSport.visibility = View.VISIBLE
                binding.tennisApiKeyRow.visibility = View.GONE
                // Le sélecteur équipe/joueur/ligue n'a de sens qu'avec
                // TheSportsDB ; masqué en tennis (recherche par joueur
                // uniquement).
                binding.radioSearchMode.visibility = View.VISIBLE
                binding.editSearch.hint = when (searchMode) {
                    SearchMode.TEAM -> "Nom d'équipe (ex. PSG)"
                    SearchMode.PLAYER -> "Nom de joueur (ex. Mbappé)"
                    SearchMode.LEAGUE -> "Nom de ligue (ex. Ligue 1)"
                }
                binding.textStatus.text =
                    "Cherche une équipe, un joueur ou une ligue de ${sportsDbSport.label} pour commencer"
            }
            ApiMode.LIVE_TENNIS -> {
                binding.radioSportsDbSport.visibility = View.GONE
                binding.radioSearchMode.visibility = View.GONE
                binding.tennisApiKeyRow.visibility = View.VISIBLE
                // Pré-remplit avec la clé déjà enregistrée, si elle existe
                // (voir TennisApiKeyPrefs.kt), pour que Yann la voie/la
                // corrige sans avoir à la recoller depuis zéro.
                binding.editTennisApiKey.setText(TennisApiKeyPrefs.get(this) ?: "")
                binding.editSearch.hint = "Nom de joueur (ex. Alcaraz)"
                binding.textStatus.text = if (TennisApiKeyPrefs.get(this) == null) {
                    "Renseigne ta clé Live Tennis API ci-dessus pour commencer"
                } else {
                    "Cherche un joueur de tennis pour commencer"
                }
            }
        }
    }
}
