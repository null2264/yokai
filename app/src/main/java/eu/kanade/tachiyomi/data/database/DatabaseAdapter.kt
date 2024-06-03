package eu.kanade.tachiyomi.data.database

import eu.kanade.tachiyomi.source.model.UpdateStrategy
import java.util.Date

// TODO: Move to dev.yokai.data.DatabaseAdapter

val updateStrategyAdapter = object : ColumnAdapter<UpdateStrategy, Int> {
    private val enumValues by lazy { UpdateStrategy.entries }

    override fun decode(databaseValue: Int): UpdateStrategy =
        enumValues.getOrElse(databaseValue) { UpdateStrategy.ALWAYS_UPDATE }

    override fun encode(value: UpdateStrategy): Int = value.ordinal
}

interface ColumnAdapter<T : Any, S> {
    /**
     * @return [databaseValue] decoded as type [T].
     */
    fun decode(databaseValue: S): T

    /**
     * @return [value] encoded as database type [S].
     */
    fun encode(value: T): S
}
