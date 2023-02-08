package de.hpi.etranslation

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.bluelinelabs.conductor.Controller
import com.bluelinelabs.conductor.archlifecycle.LifecycleController

/**
 * One stop shop for controller lifecycles: Activity, Controller, View lifecycles are all here
 */
abstract class ViewLifecycleController(bundle: Bundle?) : LifecycleController(bundle) {

    constructor() : this(null)

    var viewLifecycleOwner: ViewLifecycleOwner<ViewLifecycleController>? = null

    val requireViewLifecycleOwner: ViewLifecycleOwner<ViewLifecycleController>
        get() = viewLifecycleOwner!!

    val requireActivityLifecycleOwner: LifecycleOwner
        get() = activity as AppCompatActivity

    init {
        addLifecycleListener(
            object : LifecycleListener() {
                override fun preCreateView(controller: Controller) {
                    viewLifecycleOwner = ViewLifecycleOwner(this@ViewLifecycleController)
                }

                override fun postDestroyView(controller: Controller) {
                    viewLifecycleOwner = null
                }
            },
        )
    }
}

class ViewLifecycleOwner<T>(
    lifecycleController: T,
) : LifecycleOwner where T : Controller, T : LifecycleOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)

    init {
        lifecycleController.addLifecycleListener(
            object : Controller.LifecycleListener() {
                override fun postCreateView(controller: Controller, view: View) {
                    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
                    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
                }

                override fun postAttach(controller: Controller, view: View) {
                    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
                }

                override fun preDetach(controller: Controller, view: View) {
                    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
                }

                override fun preDestroyView(controller: Controller, view: View) {
                    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
                    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
                    lifecycleController.removeLifecycleListener(this)
                }
            },
        )
    }

    override fun getLifecycle(): Lifecycle {
        return lifecycleRegistry
    }
}
