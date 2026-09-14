package com.yann.sportscomplication.mobile

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class TeamsAdapter(
    private val teams: List<TeamResult>,
    private val onTeamClicked: (TeamResult) -> Unit
) : RecyclerView.Adapter<TeamsAdapter.ViewHolder>() {

    class ViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_team, parent, false) as TextView
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val team = teams[position]
        holder.textView.text = team.name
        holder.textView.setOnClickListener { onTeamClicked(team) }
    }

    override fun getItemCount(): Int = teams.size
}
