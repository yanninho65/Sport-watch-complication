package com.yann.sportscomplication.mobile

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.yann.sportscomplication.mobile.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    companion object {
        private const val MATCH_PATH = "/match"
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
        // TODO(prochaine étape) : envoyer aussi les logos des deux équipes
        // (téléchargés + convertis en Asset) — pour l'instant seul le
        // texte est transmis, la montre garde son icône placeholder.
        val request = PutDataMapRequest.create(MATCH_PATH).apply {
            dataMap.putString("homeTeam", match.homeTeam)
            dataMap.putString("awayTeam", match.awayTeam)
            dataMap.putString("homeScore", match.homeScore ?: "")
            dataMap.putString("awayScore", match.awayScore ?: "")
            dataMap.putString("minute", match.minuteLabel)
            // Force un DataChanged même si un match identique est
            // resélectionné (la Data Layer API ignore un putDataItem dont
            // le contenu n'a pas changé depuis le dernier envoi).
            dataMap.putLong("timestamp", System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()

        Wearable.getDataClient(this).putDataItem(request)
            .addOnSuccessListener {
                Toast.makeText(this, "Envoyé à la montre : ${match.title}", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Échec de l'envoi vers la montre", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showSearchState() {
        binding.buttonBackToSearch.visibility = android.view.View.GONE
        binding.textStatus.text = "Cherche une équipe pour commencer"
        binding.recyclerView.adapter = null
        binding.editSearch.setText("")
    }
}
