package com.rallyinreallife.app.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.lifecycleScope
import com.rallyinreallife.app.nav.RallySession
import kotlinx.coroutines.launch

/**
 * Schermata Android Auto: mostra la nota copilota corrente in grande,
 * con le prossime note sotto, come il display del navigatore in un vero rally.
 */
class RallyAutoScreen(carContext: CarContext) : Screen(carContext) {

    init {
        lifecycleScope.launch {
            RallySession.navState.collect { invalidate() }
        }
    }

    override fun onGetTemplate(): Template {
        val state = RallySession.navState.value
        val current = state.currentNote

        val mainRow = Row.Builder()
            .setTitle(current?.displayText ?: "In attesa del via\u2026")
            .addText(
                if (state.finished) "Fine speciale! \ud83c\udfc1"
                else "Tra ${format(state.metersToNextManeuver)} \u00b7 ${state.speedKmh.toInt()} km/h"
            )
            .build()

        val paneBuilder = Pane.Builder().addRow(mainRow)
        state.nextNotes.forEach { note ->
            paneBuilder.addRow(Row.Builder().setTitle(note.displayText).build())
        }

        return PaneTemplate.Builder(paneBuilder.build())
            .setHeader(
                Header.Builder()
                    .setTitle("Rally in Real Life")
                    .setStartHeaderAction(Action.APP_ICON)
                    .build()
            )
            .build()
    }

    private fun format(meters: Double): String = when {
        meters >= 1000 -> "%.1f km".format(meters / 1000.0)
        else -> "${meters.toInt()} m"
    }
}
