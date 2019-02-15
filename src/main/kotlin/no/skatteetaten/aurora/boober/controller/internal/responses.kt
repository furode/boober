package no.skatteetaten.aurora.boober.controller.internal

import com.fasterxml.jackson.annotation.JsonGetter

data class Response(
    val success: Boolean = true,
    val message: String = "OK",
    val items: List<Any> = emptyList(),
    val count: Int = items.size
)

data class KeyValueResponse<T>(
    val success: Boolean = true,
    val message: String = "OK",
    val items: Map<String, T> = emptyMap(),
    val count: Int = items.size
) {
    @JsonGetter("items")
    fun getArrayItems() = items.entries
}

data class SingleResponse<T>(
    val success: Boolean = true,
    val message: String = "OK",
    val items: T,
    val count: Int = 1
) {
    @JsonGetter("items")
    fun getArrayItems() = listOf(items)
}