package eu.kanade.tachiyomi.data.database.models

class CategoryImpl : Category {

    override var id: Int? = null

    override lateinit var name: String

    override var order: Int = 0

    override var flags: Int = 0

    override var mangaOrder: List<Long> = emptyList()

    override var mangaSort: Char? = null

    override var isAlone: Boolean = false

    override var isHidden: Boolean = false

    override var isDynamic: Boolean = false

    override var sourceId: Long? = null

    override var langId: String? = null

    override var isSystem: Boolean = false

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false

        val category = other as Category

        if (isDynamic && category.isDynamic) return dynamicHeaderKey() == category.dynamicHeaderKey()

        return name == category.name
    }

    override fun hashCode(): Int {
        if (isDynamic) return dynamicHeaderKey().hashCode()

        return name.hashCode()
    }
}
