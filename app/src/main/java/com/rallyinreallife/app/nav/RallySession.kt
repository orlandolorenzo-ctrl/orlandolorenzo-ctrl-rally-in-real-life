package com.rallyinreallife.app.nav

import com.rallyinreallife.app.pace.PaceNote
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Stato condiviso tra telefono e Android Auto: l'app sul telefono calcola
 * le note e il tracker aggiorna questo stato; la sessione Android Auto lo
 * legge per mostrare la nota corrente sul display dell'auto.
 */
object RallySession {

    private val _navState = MutableStateFlow(RallyNavState())
    val navState: StateFlow<RallyNavState> = _navState

    var route: Route? = null
        private set

    var coDriverEnabled: Boolean = true

    fun updateState(state: RallyNavState) {
        _navState.value = state
    }

    fun startWithRoute(route: Route, notes: List<PaceNote>) {
        this.route = route
        _navState.value = RallyNavState(notes = notes)
    }

    fun reset() {
        route = null
        _navState.value = RallyNavState()
    }
}
