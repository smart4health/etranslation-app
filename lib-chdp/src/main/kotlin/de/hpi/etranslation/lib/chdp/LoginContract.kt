package de.hpi.etranslation.lib.chdp

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import androidx.annotation.ColorInt
import androidx.browser.customtabs.CustomTabsIntent
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class LoginContract(
    @ColorInt private val toolbarColor: Int,
) : ActivityResultContract<Intent, Result<Intent, Intent?>>() {
    override fun createIntent(context: Context, input: Intent) =
        input.apply {
            // revered engineered slightly to match the custom tabs color
            // null access operator ensures that if it breaks, nothing crashes
            (
                (extras?.get("AUTHORIZATION_INTENT") as? Intent)
                    ?.extras
                    ?.get("authIntent") as? Intent
                )
                ?.putExtra(CustomTabsIntent.EXTRA_TOOLBAR_COLOR, toolbarColor)
        }

    override fun parseResult(resultCode: Int, intent: Intent?): Result<Intent, Intent?> = when {
        resultCode == Activity.RESULT_OK && intent != null -> Ok(intent)
        else -> Err(intent)
    }
}
