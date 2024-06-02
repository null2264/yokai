package dev.yokai.data

import app.cash.sqldelight.Query
import app.cash.sqldelight.Transacter
import kotlinx.coroutines.flow.Flow
import tachiyomi.data.Database

interface DatabaseHandler {
    suspend fun <T> await(inTransaction: Boolean = false, block: suspend Database.() -> T): T

    suspend fun <T : Any> awaitList(
        inTransaction: Boolean = false,
        block: suspend Database.() -> Query<T>
    ): List<T>

    suspend fun <T : Any> awaitOne(
        inTransaction: Boolean = false,
        block: suspend Database.() -> Query<T>
    ): T

    suspend fun <T : Any> awaitOneOrNull(
        inTransaction: Boolean = false,
        block: suspend Database.() -> Query<T>
    ): T?

    fun <T : Any> subscribeToList(block: Database.() -> Query<T>): Flow<List<T>>

    fun <T : Any> subscribeToOne(block: Database.() -> Query<T>): Flow<T>

    fun <T : Any> subscribeToOneOrNull(block: Database.() -> Query<T>): Flow<T?>

    /*
    fun <T : Any> subscribeToPagingSource(
        countQuery: Database.() -> Query<Long>,
        transacter: Database.() -> Transacter,
        queryProvider: Database.(Long, Long) -> Query<T>
    ): PagingSource<Long, T>
     */
}
