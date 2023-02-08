package de.hpi.etranslation.feature.dashboard

import android.view.View

/**
 * Not sure if this is the best solution but it is the easiest right now
 *
 * I think a fully proper solution would somehow share the coordinator layout
 * with the parent controller
 *
 * Right now this doesn't animate if the fab disappears and I'm not sure
 * if that's possible
 */
interface HasAnchorView {
    fun anchorView(): View?
}
