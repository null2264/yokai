package yokai.domain.source.browse.filter

import eu.kanade.tachiyomi.source.model.Filter
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addAll
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

interface Serializer<in T : Filter<out Any?>> {
    fun JsonObjectBuilder.serialize(filter: T) {}
    fun deserialize(json: JsonObject, filter: T) {}

    fun mappings(): List<Pair<String, KProperty1<in T, *>>> = emptyList()

    val serializer: FilterSerializer
    val type: String
    val clazz: KClass<in T>

    companion object {
        const val TYPE = "_type"
        const val NAME = "name"
        const val STATE = "state"
    }
}

class HeaderSerializer(override val serializer: FilterSerializer) : Serializer<Filter.Header> {
    override val type = "HEADER"
    override val clazz = Filter.Header::class

    override fun mappings() = listOf(
        Serializer.NAME to Filter.Header::name,
    )
}

class SeparatorSerializer(override val serializer: FilterSerializer) : Serializer<Filter.Separator> {
    override val type = "SEPARATOR"
    override val clazz = Filter.Separator::class

    override fun mappings() = listOf(
        Serializer.NAME to Filter.Separator::name,
    )
}

class SelectSerializer(override val serializer: FilterSerializer) : Serializer<Filter.Select<Any>> {
    override val type = "SELECT"
    override val clazz = Filter.Select::class

    override fun JsonObjectBuilder.serialize(filter: Filter.Select<Any>) {
        putJsonArray("values") {
            addAll(filter.values.map { it.toString() })
        }
    }

    override fun mappings() = listOf(
        Serializer.NAME to Filter.Select<Any>::name,
        Serializer.STATE to Filter.Select<Any>::state,
    )
}

class TextSerializer(override val serializer: FilterSerializer) : Serializer<Filter.Text> {
    override val type = "TEXT"
    override val clazz = Filter.Text::class

    override fun mappings() = listOf(
        Serializer.NAME to Filter.Text::name,
        Serializer.STATE to Filter.Text::state,
    )
}

class CheckBoxSerializer(override val serializer: FilterSerializer) : Serializer<Filter.CheckBox> {
    override val type = "CHECKBOX"
    override val clazz = Filter.CheckBox::class

    override fun mappings() = listOf(
        Serializer.NAME to Filter.CheckBox::name,
        Serializer.STATE to Filter.CheckBox::state,
    )
}

class TriStateSerializer(override val serializer: FilterSerializer) : Serializer<Filter.TriState> {
    override val type = "TRI_STATE"
    override val clazz = Filter.TriState::class

    override fun mappings() = listOf(
        Serializer.NAME to Filter.TriState::name,
        Serializer.STATE to Filter.TriState::state,
    )
}

class GroupSerializer(override val serializer: FilterSerializer) : Serializer<Filter.Group<Any?>> {
    override val type = "GROUP"
    override val clazz = Filter.Group::class

    override fun JsonObjectBuilder.serialize(filter: Filter.Group<Any?>) {
        putJsonObject(VALUES) {
            filter.state.forEach { item ->
                @Suppress("UNCHECKED_CAST", "SafeCastWithReturn")
                item as? Filter<Any?> ?: return@forEach
                // Assuming `item.name` is unique, not sure if it is tho...
                put(item.name, serializer.serialize(item))
            }
        }
    }

    override fun deserialize(json: JsonObject, filter: Filter.Group<Any?>) {
        val values = json[VALUES]?.jsonObject
        if (values == null) {
            // TODO: Delete later
            json[Serializer.STATE]?.jsonArray?.forEachIndexed { index, element ->
                if (element == JsonNull) return@forEachIndexed

                @Suppress("UNCHECKED_CAST")
                serializer.deserialize(filter.state[index] as Filter<Any?>, element.jsonObject)
            }
            return
        }

        filter.state.forEach { item ->
            @Suppress("UNCHECKED_CAST", "SafeCastWithReturn")
            item as? Filter<Any?> ?: return@forEach

            val itemJson = values[item.name]?.jsonObject ?: return@forEach
            serializer.deserialize(item, itemJson)
        }
    }

    override fun mappings() = listOf(
        Serializer.NAME to Filter.Group<Any?>::name,
    )

    companion object {
        const val VALUES = "values"
    }
}

class SortSerializer(override val serializer: FilterSerializer) : Serializer<Filter.Sort> {
    override val type = "SORT"
    override val clazz = Filter.Sort::class

    override fun JsonObjectBuilder.serialize(filter: Filter.Sort) {
        putJsonArray(VALUES) {
            filter.values.forEach { add(it) }
        }

        put(
            Serializer.STATE,
            filter.state?.let { (index, ascending) ->
                buildJsonObject {
                    put(STATE_INDEX, index)
                    put(STATE_ASCENDING, ascending)
                }
            } ?: JsonNull,
        )
    }

    override fun deserialize(json: JsonObject, filter: Filter.Sort) {
        filter.state = (json[Serializer.STATE] as? JsonObject)?.let {
            Filter.Sort.Selection(
                it[STATE_INDEX]!!.jsonPrimitive.int,
                it[STATE_ASCENDING]!!.jsonPrimitive.boolean,
            )
        }
    }

    override fun mappings() = listOf(
        Pair(Serializer.NAME, Filter.Sort::name),
    )

    companion object {
        const val VALUES = "values"

        const val STATE_INDEX = "index"
        const val STATE_ASCENDING = "ascending"
    }
}
