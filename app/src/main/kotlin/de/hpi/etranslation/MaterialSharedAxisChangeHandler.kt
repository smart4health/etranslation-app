package de.hpi.etranslation

import android.view.View
import android.view.ViewGroup
import androidx.transition.Transition
import com.bluelinelabs.conductor.changehandler.androidxtransition.TransitionChangeHandler
import com.google.android.material.transition.MaterialSharedAxis

class MaterialSharedAxisChangeHandler @JvmOverloads constructor(
    @MaterialSharedAxis.Axis
    val axis: Int = MaterialSharedAxis.X,
    val backwards: Boolean = false,
    private val removesFromViewOnPush: Boolean = true,
) : TransitionChangeHandler() {

    override fun removesFromViewOnPush() = removesFromViewOnPush

    override fun getTransition(
        container: ViewGroup,
        from: View?,
        to: View?,
        isPush: Boolean,
    ): Transition = MaterialSharedAxis(axis, if (backwards) false else isPush)
}
