package com.yann.sportscomplication

import android.content.Context

/**
 * Mémorise, pour chaque emplacement de complication (identifié par son
 * complicationInstanceId), si ce cercle doit afficher le logo de
 * l'équipe à DOMICILE ou à L'EXTÉRIEUR. Réglé via TeamSideConfigActivity
 * au moment où l'utilisateur assigne le fournisseur à un cercle.
 */
object TeamSidePrefs {

    const val SIDE_HOME = "home"
    const val SIDE_AWAY = "away"

    private const val PREFS_NAME = "team_side_prefs"
    private const val KEY_PREFIX = "side_"

    fun getSide(context: Context, complicationInstanceId: Int): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString("$KEY_PREFIX$complicationInstanceId", SIDE_HOME) ?: SIDE_HOME
    }

    fun setSide(context: Context, complicationInstanceId: Int, side: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString("$KEY_PREFIX$complicationInstanceId", side).apply()
    }
}
