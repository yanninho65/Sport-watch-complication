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
 * Recherche un match (par équipe, joueur ou ligue) et lance son suivi.
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

        when (searchMode) {
            SearchMode.TEAM -> searchTeams(query)
            SearchMode.PLAYER -> searchPlayers(query)
            SearchMode.LEAGUE -> searchLeagues(query)
        }
    }

    private fun searchTeams(query: String) {
        lifecycleScope.launch {
            val teams = safeCall { SportsDbApi.searchTeams(query) }
            if (teams.isEmpty()) {
                binding.textStatus.text = "Aucune équipe trouvée pour \"$query\""
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
            val players = safeCall { SportsDbApi.searchPlayers(query) }
            if (players.isEmpty()) {
                binding.textStatus.text = "Aucun joueur trouvé pour \"$query\""
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
            val leagues = safeCall { SportsDbApi.searchLeagues(query) }
            if (leagues.isEmpty()) {
                binding.textStatus.text = "Aucune ligue trouvée pour \"$query\""
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
        binding.textStatus.text = "Cherche une équipe, un joueur ou une ligue pour commencer"
        binding.recyclerView.adapter = null
        binding.editSearch.setText("")
    }
}
