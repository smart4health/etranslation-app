package de.hpi.etranslation.data

import com.squareup.sqldelight.ColumnAdapter

class ListAdapter<T : Any, A>(
    private val adapter: A,
    private val separator: String,
) : ColumnAdapter<List<T>, String> where A : ColumnAdapter<T, String> {
    override fun decode(databaseValue: String): List<T> = databaseValue.split(separator)
        .filter(String::isNotEmpty)
        .map(adapter::decode)

    override fun encode(value: List<T>): String = value.joinToString(
        separator = separator,
        transform = adapter::encode,
    )
}

fun <T : Any, A : ColumnAdapter<T, String>> A.asListAdapter(
    separator: String = ",",
): ColumnAdapter<List<T>, String> = ListAdapter(this, separator)
