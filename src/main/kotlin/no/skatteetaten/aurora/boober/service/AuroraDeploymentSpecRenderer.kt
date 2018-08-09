package no.skatteetaten.aurora.boober.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.convertValue
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.skatteetaten.aurora.boober.mapper.present
import no.skatteetaten.aurora.boober.mapper.removeDefaults
import no.skatteetaten.aurora.boober.mapper.removeInactive
import no.skatteetaten.aurora.boober.model.AuroraDeploymentSpec

fun renderSpecAsJson(deploymentSpec: AuroraDeploymentSpec, includeDefaults: Boolean): JsonNode {
    return jacksonObjectMapper().convertValue(getFieldsForPresentation(includeDefaults, deploymentSpec))
}

fun renderJsonForAuroraDeploymentSpecPointers(deploymentSpec: AuroraDeploymentSpec, includeDefaults: Boolean): String {

    val fields = getFieldsForPresentation(includeDefaults, deploymentSpec)

    val defaultKeys = listOf("source", "value")
    val indent = 2

    val keyMaxLength = findMaxKeyLength(fields, indent)
    val valueMaxLength = findMaxValueLength(fields)

    fun renderJson(level: Int, result: String, entry: Map.Entry<String, Map<String, Any?>>): String {

        val key = entry.key
        val value = entry.value["value"].toString()
        val source = entry.value["source"].toString()
        val indents = " ".repeat(level * indent)

        return if (entry.value.keys.all { defaultKeys.indexOf(it) != -1 }) {
            val keySpaces = " ".repeat(keyMaxLength + 2 - key.length - level * 2)
            val valueSpaces = " ".repeat(valueMaxLength + 1 - value.length)

            "$result$indents$key:$keySpaces$value$valueSpaces// $source\n"
        } else {
            val nextObject = indents + "$key: {\n"
            val nextObjectResult = entry.value
                .entries
                .filter { defaultKeys.indexOf(it.key) == -1 }
                .fold(nextObject) { res, e ->
                    res + renderJson(level + 1, "", e as Map.Entry<String, Map<String, Any?>>)
                }
            result + nextObjectResult + indents + "}\n"
        }
    }

    return fields.entries
        .fold("{\n") { result, entry ->
            renderJson(1, result, entry as Map.Entry<String, Map<String, Any?>>)
        } + "}"
}

fun getFieldsForPresentation(
    includeDefaults: Boolean,
    deploymentSpec: AuroraDeploymentSpec
): Map<String, Any> {
    val rawFields = if (!includeDefaults) {
        deploymentSpec.fields.removeDefaults()
    } else {
        deploymentSpec.fields
    }
    val fields = rawFields.removeInactive().present { mapOf("source" to it.value.source, "value" to it.value.value) }
    return fields
}

fun findMaxKeyLength(fields: Map<String, Any>, indent: Int, accumulated: Int = 0): Int {
    return fields.map {
        val value = it.value as Map<String, Any>
        if (value.containsKey("source")) {
            it.key.length + accumulated
        } else {
            findMaxKeyLength(value, indent, accumulated + indent)
        }
    }.max()?.let { it + 1 } ?: 0
}

fun findMaxValueLength(fields: Map<String, Any>): Int {
    return fields.map {
        val value = it.value as Map<String, Any>
        if (value.containsKey("value")) {
            value["value"].toString().length
        } else {
            findMaxValueLength(value)
        }
    }.max() ?: 0
}
