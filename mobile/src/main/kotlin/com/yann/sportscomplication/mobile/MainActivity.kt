package com.yann.sportscomplication.mobile

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.yann.sportscomplication.mobile.databinding.ActivityMainBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    /** Boucle de rafraîchissement du match actuellement suivi, s'il y en a un. */
    private var followJob: Job? = null

    /**
     * Logos mis en cache après le premier envoi, pour ne pas les
     * retélécharger à chaque tick du polling — on les rejoint à chaque
     * mise à jour envoyée pour que la montre garde toujours l'image
     * (chaque nouvelle donnée reçue remplace entièrement l'ancienne côté
     * montre, assets compris).
     */
    private var cachedHomeLogo: Asset? = null
    private var cachedAwayLogo: Asset? = null

    companion object {
        private const val MATCH_PATH = "/match"
        private const val POLL_INTERVAL_MS = 60_000L
    }

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
    }

    private fun runSearch() {
        val query = binding.editSearch.text?.toString()?.trim().orEmpty()
        if (query.isEmpty()) return

        binding.textStatus.text = "Recherche de \"$query\"…"
        lifecycleScope.launch {
            val teams = try {
                SportsDbApi.searchTeams(query)
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Erreur réseau", Toast.LENGTH_SHORT).show()
                emptyList()
            }

            if (teams.isEmpty()) {
                binding.textStatus.text = "Aucune équipe trouvée pour \"$query\""
                binding.recyclerView.adapter = null
                return@launch
            }

            binding.textStatus.text = "${teams.size} équipe(s) trouvée(s) — choisis-en une"
            binding.recyclerView.adapter = TeamsAdapter(teams) { team -> onTeamSelected(team) }
        }
    }

    private fun onTeamSelected(team: TeamResult) {
        binding.textStatus.text = "Chargement des matchs de ${team.name}…"
        binding.buttonBackToSearch.visibility = android.view.View.VISIBLE

        lifecycleScope.launch {
            val matches = try {
                SportsDbApi.getMatchesForTeam(team.id)
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Erreur réseau", Toast.LENGTH_SHORT).show()
                emptyList()
            }

            if (matches.isEmpty()) {
                binding.textStatus.text = "Aucun match trouvé pour ${team.name}"
                binding.recyclerView.adapter = null
                return@launch
            }

            binding.textStatus.text = "Matchs de ${team.name} — choisis celui à suivre"
            binding.recyclerView.adapter = MatchesAdapter(matches) { match -> onMatchSelected(match) }
        }
    }

    private fun onMatchSelected(match: MatchResult) {
        followJob?.cancel()
        cachedHomeLogo = null
        cachedAwayLogo = null

        followJob = lifecycleScope.launch {
            // Premier envoi : télécharge les logos et les met en cache.
            fetchLogos(match)
            sendMatchToWatch(match)
            Toast.makeText(this@MainActivity, "Envoyé à la montre : ${match.title}", Toast.LENGTH_SHORT).show()

            // Rafraîchissement périodique tant que le match n'est pas terminé.
            var current = match
            while (isActive && !current.isFinished) {
                delay(POLL_INTERVAL_MS)
                val updated = try {
                    SportsDbApi.lookupEvent(current.id)
                } catch (e: Exception) {
                    null
                } ?: continue

                if (updated != current) {
                    sendMatchToWatch(updated)
                    current = updated
                }
            }
        }
    }

    private suspend fun fetchLogos(match: MatchResult) {
        cachedHomeLogo = match.idHomeTeam?.let { downloadLogoAsset(it) }
        cachedAwayLogo = match.idAwayTeam?.let { downloadLogoAsset(it) }
    }

    private suspend fun downloadLogoAsset(teamId: String): Asset? {
        val url = try {
            SportsDbApi.getTeamBadgeUrl(teamId)
        } catch (e: Exception) {
            null
        } ?: return null

        val bytes = try {
            SportsDbApi.downloadBytes(url)
        } catch (e: Exception) {
            null
        } ?: return null

        return Asset.createFromBytes(bytes)
    }

    private fun sendMatchToWatch(match: MatchResult) {
        val request = PutDataMapRequest.create(MATCH_PATH).apply {
            dataMap.putString("homeTeam", match.homeTeam)
            dataMap.putString("awayTeam", match.awayTeam)
            dataMap.putString("homeScore", match.homeScore ?: "")
            dataMap.putString("awayScore", match.awayScore ?: "")
            dataMap.putString("status", match.status)
            match.kickoffEpochMillis?.let { dataMap.putLong("kickoffEpochMillis", it) }
            cachedHomeLogo?.let { dataMap.putAsset("homeLogo", it) }
            cachedAwayLogo?.let { dataMap.putAsset("awayLogo", it) }
            // Force un DataChanged même si le contenu texte n'a pas bougé
            // depuis le dernier envoi (la Data Layer API ignore sinon un
            // putDataItem identique au précédent).
            dataMap.putLong("timestamp", System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()

        Wearable.getDataClient(this).putDataItem(request)
            .addOnFailureListener {
                Toast.makeText(this, "Échec de l'envoi vers la montre", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showSearchState() {
        followJob?.cancel()
        binding.buttonBackToSearch.visibility = android.view.View.GONE
        binding.textStatus.text = "Cherche une équipe pour commencer"
        binding.recyclerView.adapter = null
        binding.editSearch.setText("")
    }
}
