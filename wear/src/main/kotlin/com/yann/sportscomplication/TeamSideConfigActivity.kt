package com.yann.sportscomplication

import android.app.Activity
import android.os.Bundle
import android.widget.Button

/**
 * Écran affiché par le système quand l'utilisateur assigne "Score en
 * direct" à un emplacement de complication cercle (Dashboard Samsung).
 * Permet de choisir si CET emplacement précis doit afficher le logo de
 * l'équipe à domicile ou à l'extérieur — chaque cercle retient son
 * propre choix (voir TeamSidePrefs).
 */
class TeamSideConfigActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_team_side_config)

        // Valeur littérale plutôt qu'une constante importée de la librairie
        // (androidx.wear.watchface.complications.datasource) : cette clé
        // n'est pas exposée de façon stable/résolvable dans la version
        // 1.3.0 utilisée ici, mais la chaîne elle-même est documentée et
        // stable (héritée de l'ancienne android.support.wearable.*).
        val complicationId = intent.getIntExtra(
            "android.support.wearable.complications.EXTRA_CONFIG_COMPLICATION_ID",
            -1
        )

        findViewById<Button>(R.id.buttonHome).setOnClickListener {
            confirm(complicationId, TeamSidePrefs.SIDE_HOME)
        }
        findViewById<Button>(R.id.buttonAway).setOnClickListener {
            confirm(complicationId, TeamSidePrefs.SIDE_AWAY)
        }
    }

    private fun confirm(complicationId: Int, side: String) {
        if (complicationId != -1) {
            TeamSidePrefs.setSide(this, complicationId, side)
        }
        setResult(RESULT_OK)
        finish()
    }
}
