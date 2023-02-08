@file:Suppress("unused")

package de.hpi.etranslation

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

inline fun <T1, T2, T3, R> Flow<T1>.combine3(
    second: Flow<T2>,
    third: Flow<T3>,
    crossinline transform: suspend (T1, T2, T3) -> R,
): Flow<R> = combine(second) { a, b ->
    a to b
}.combine(third) { (a, b), c ->
    transform(a, b, c)
}

inline fun <T1, T2, T3, T4, R> Flow<T1>.combine4(
    second: Flow<T2>,
    third: Flow<T3>,
    fourth: Flow<T4>,
    crossinline transform: suspend (T1, T2, T3, T4) -> R,
): Flow<R> = combine3(second, third) { a, b, c ->
    Triple(a, b, c)
}.combine(fourth) { (a, b, c), d ->
    transform(a, b, c, d)
}
