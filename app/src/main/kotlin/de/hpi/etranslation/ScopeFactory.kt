package de.hpi.etranslation

import kotlinx.coroutines.CoroutineScope

/**
 * Small helper for view model assisted inject factories
 */
interface ScopeFactory<T> {
    fun create(coroutineScope: CoroutineScope): T
}
