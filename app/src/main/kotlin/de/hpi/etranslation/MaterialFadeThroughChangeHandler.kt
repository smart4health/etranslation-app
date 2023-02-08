package de.hpi.etranslation

import android.view.View
import android.view.ViewGroup
import androidx.transition.Transition
import com.bluelinelabs.conductor.changehandler.androidxtransition.TransitionChangeHandler
import com.google.android.material.transition.MaterialFadeThrough

class MaterialFadeThroughChangeHandler(
    private val removesFromViewOnPush: Boolean = true,
) : TransitionChangeHandler() {

    override fun removesFromViewOnPush(): Boolean = removesFromViewOnPush

    override fun getTransition(
        container: ViewGroup,
        from: View?,
        to: View?,
        isPush: Boolean,
    ): Transition = MaterialFadeThrough().also { transition ->
        // these addTarget calls will make the transitions treat
        // the screens as one view instead of transitioning individual
        // children

        // this also happens to treat the flickering GONE/INVISIBLE
        // elements, still no idea of the root cause but another fix
        // is to exclude them from the transition (instead of adding
        // the root of each screen)

        if (from != null)
            transition.addTarget(from)

        if (to != null)
            transition.addTarget(to)
    }
}
