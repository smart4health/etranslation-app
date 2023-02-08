package de.hpi.etranslation

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.datastore.core.DataStore
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bluelinelabs.conductor.Conductor
import com.bluelinelabs.conductor.Router
import com.bluelinelabs.conductor.asTransaction
import dagger.hilt.android.AndroidEntryPoint
import de.hpi.etranslation.databinding.ActivityMainBinding
import de.hpi.etranslation.feature.dashboard.DashboardController
import de.hpi.etranslation.feature.onboard.OnboardController
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var dataStore: DataStore<Settings>

    @Inject
    lateinit var mainEventReceiver: @JvmSuppressWildcards ReceiveChannel<MainEvent>

    private lateinit var router: Router

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        ActivityMainBinding.inflate(layoutInflater).apply {
            setContentView(root)

            router = Conductor.attachRouter(this@MainActivity, root, savedInstanceState)
                .setPopRootControllerMode(Router.PopRootControllerMode.NEVER)

            if (router.backstack.isEmpty()) {
                lifecycleScope.launch {
                    if (dataStore.data.first().isConsented) {
                        DashboardController()
                            .asTransaction()
                            .let(router::setRoot)
                    } else
                        OnboardController()
                            .asTransaction()
                            .let(router::setRoot)
                }
            }

            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    mainEventReceiver.receiveAsFlow().collect {
                        when (it) {
                            MainEvent.LOGGED_IN -> DashboardController()
                                .asTransaction()
                                .pushChangeHandler(MaterialSharedAxisChangeHandler())
                                .let(router::replaceTopController)
                            MainEvent.LOGGED_OUT -> OnboardController()
                                .asTransaction()
                                .pushChangeHandler(MaterialSharedAxisChangeHandler(backwards = true))
                                .let(router::replaceTopController)
                        }
                    }
                }
            }
        }
    }

    override fun onBackPressed() {
        if (!router.handleBack()) {
            super.onBackPressed()
        }
    }
}

enum class MainEvent {
    LOGGED_IN,
    LOGGED_OUT,
}
