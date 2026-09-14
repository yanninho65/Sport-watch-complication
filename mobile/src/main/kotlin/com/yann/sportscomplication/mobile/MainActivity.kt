package com.yann.sportscomplication.mobile

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.yann.sportscomplication.mobile.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

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
        // TODO(prochaine étape) : envoyer ce match vers la montre via la
        // Wear Data Layer API (DataClient), à la place de ce Toast.
        Toast.makeText(this, "Sélectionné : ${match.title}", Toast.LENGTH_LONG).show()
    }

    private fun showSearchState() {
        binding.buttonBackToSearch.visibility = android.view.View.GONE
        binding.textStatus.text = "Cherche une équipe pour commencer"
        binding.recyclerView.adapter = null
        binding.editSearch.setText("")
    }
}
