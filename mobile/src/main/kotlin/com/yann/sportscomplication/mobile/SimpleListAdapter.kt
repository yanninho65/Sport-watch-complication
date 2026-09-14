package com.yann.sportscomplication.mobile

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * Adaptateur générique pour une simple liste "nom + clic" — remplace
 * l'ancien TeamsAdapter (spécifique à TeamResult) maintenant que la
 * recherche gère aussi les joueurs et les ligues, qui partagent la même
 * présentation à l'écran (un nom, un clic).
 */
class SimpleListAdapter<T>(
    private val items: List<T>,
    private val displayName: (T) -> String,
    private val onClicked: (T) -> Unit
) : RecyclerView.Adapter<SimpleListAdapter.ViewHolder>() {

    class ViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_team, parent, false) as TextView
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.textView.text = displayName(item)
        holder.textView.setOnClickListener { onClicked(item) }
    }

    override fun getItemCount(): Int = items.size
}
