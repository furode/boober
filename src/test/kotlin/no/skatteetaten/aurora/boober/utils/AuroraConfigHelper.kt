package no.skatteetaten.aurora.boober.utils

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.skatteetaten.aurora.boober.model.ApplicationDeploymentRef
import no.skatteetaten.aurora.boober.model.AuroraConfig
import no.skatteetaten.aurora.boober.model.AuroraConfigFile
import java.io.File
import java.nio.charset.Charset

class AuroraConfigHelper

val folder = File(AuroraConfigHelper::class.java.getResource("/samples/config").file)

fun getAuroraConfigSamples(): AuroraConfig {
    val files = folder.walkBottomUp()
        .onEnter { it.name != "secret" }
        .filter { it.isFile }
        .associate { it.relativeTo(folder).path to it }

    val nodes = files.map {
        it.key to it.value.readText(Charset.defaultCharset())
    }.toMap()

    return AuroraConfig(nodes.map {
        AuroraConfigFile(
            it.key,
            it.value,
            false
        )
    }, "aos", "master")
}

@JvmOverloads
fun getSampleFiles(aid: ApplicationDeploymentRef, additionalFile: String? = null): Map<String, String> {

    return collectFiles(
        "about.json",
        "${aid.application}.json",
        "${aid.environment}/about.json",
        "${aid.environment}/${aid.application}.json",
        additionalFile?.let { it } ?: ""
    )
}

fun getResultFiles(aid: ApplicationDeploymentRef): Map<String, JsonNode?> {
    val baseFolder = File(
        AuroraConfigHelper::class.java
            .getResource("/samples/result/${aid.environment}/${aid.application}").file
    )

    return getFiles(baseFolder)
}

private fun getFiles(baseFolder: File, name: String = ""): Map<String, JsonNode?> {
    return baseFolder.listFiles().toHashSet().associate {
        val json = convertFileToJsonNode(it)

        var appName = json?.at("/metadata/name")?.textValue()
        if (name.isNotBlank()) {
            appName = name
        }

        val file = json?.at("/kind")?.textValue() + "/" + appName
        file.toLowerCase() to json
    }
}

private fun collectFiles(vararg fileNames: String): Map<String, String> {

    return fileNames.filter { !it.isBlank() }.associateWith { File(folder, it).readText(Charset.defaultCharset()) }
}

private fun convertFileToJsonNode(file: File): JsonNode? {

    val mapper = jacksonObjectMapper()
    return mapper.readValue(file, JsonNode::class.java)
}