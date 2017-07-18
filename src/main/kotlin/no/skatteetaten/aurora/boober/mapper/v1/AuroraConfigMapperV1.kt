package no.skatteetaten.aurora.boober.mapper.v1

import com.fasterxml.jackson.databind.JsonNode
import no.skatteetaten.aurora.boober.mapper.AuroraConfigFieldHandler
import no.skatteetaten.aurora.boober.mapper.AuroraConfigMapper
import no.skatteetaten.aurora.boober.model.*
import no.skatteetaten.aurora.boober.service.internal.AuroraConfigException
import no.skatteetaten.aurora.boober.service.openshift.OpenShiftClient
import no.skatteetaten.aurora.boober.utils.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory

abstract class AuroraConfigMapperV1(
        aid: DeployCommand,
        auroraConfig: AuroraConfig,
        openShiftClient: OpenShiftClient
) : AuroraConfigMapper(aid, auroraConfig) {

    override val logger: Logger = LoggerFactory.getLogger(AuroraConfigMapperV1::class.java)

    val applicationFiles = auroraConfig.getFilesForApplication(aid)
    val configHandlers = findConfigFieldHandlers(applicationFiles)
    val parameterHandlers = findParameters(applicationFiles)

    val mountHandlers = findMounts(applicationFiles)
    val routeHandlers = findRouteAnnotaionHandlers(applicationFiles)
    val v1Handlers = baseHandlers + routeHandlers + configHandlers + mountHandlers + parameterHandlers + listOf(
            AuroraConfigFieldHandler("affiliation", validator = { it.pattern("^[a-z]{0,23}[a-z]$", "Affiliation is must be alphanumeric and under 24 characters") }),
            AuroraConfigFieldHandler("cluster", validator = { it.notBlank("Cluster must be set") }),
            AuroraConfigFieldHandler("name", validator = { it.pattern("^[a-z][-a-z0-9]{0,23}[a-z0-9]$", "Name must be alphanumeric and under 24 characters") }),
            AuroraConfigFieldHandler("envName"),
            AuroraConfigFieldHandler("permissions/admin/groups", validator = validateGroups(openShiftClient)),
            AuroraConfigFieldHandler("permissions/admin/users", validator = validateUsers(openShiftClient)),
            AuroraConfigFieldHandler("permissions/view/groups", validator = validateGroups(openShiftClient, false)),
            AuroraConfigFieldHandler("permissions/view/users", validator = validateUsers(openShiftClient)),
            AuroraConfigFieldHandler("database"),
            AuroraConfigFieldHandler("route/host"),
            AuroraConfigFieldHandler("route/path", validator = { it?.startsWith("/", "Path must start with /") }),
            AuroraConfigFieldHandler("route/generate", defaultValue = "false"),
            AuroraConfigFieldHandler("webseal/host"),
            AuroraConfigFieldHandler("webseal/roles"),
            AuroraConfigFieldHandler("secretFolder", validator = validateSecrets(auroraConfig))
    )

    fun getRoute(): Route? {

        val generateRoute = auroraConfigFields.extract("route/generate", { it.asText() == "true" })
        logger.debug("Route generate, {}", generateRoute)
        val routePath = auroraConfigFields.extractOrNull("route/path")
        val routeHost = auroraConfigFields.extractOrNull("route/host")
        routeHandlers.forEach { it -> logger.debug("{}", it.toString()) }

        return if (generateRoute || routeHost != null || routePath != null || !routeHandlers.isEmpty()) {
            Route(routeHost, routePath, auroraConfigFields.getRouteAnnotations(routeHandlers))
        } else null
    }

    fun findConfigFieldHandlers(applicationFiles: List<AuroraConfigFile>): List<AuroraConfigFieldHandler> {

        val name = "config"
        val configFiles = findSubKeys(applicationFiles, name)

        val configKeys: Map<String, Set<String>> = configFiles.map { configFileName ->
            //find all unique keys in a configFile
            val keys = applicationFiles.flatMap { ac ->
                ac.contents.at("/$name/$configFileName")?.fieldNames()?.asSequence()?.toList() ?: emptyList()
            }.toSet()

            configFileName to keys
        }.toMap()

        return configKeys.flatMap { configFile ->
            configFile.value.map { field ->
                AuroraConfigFieldHandler("$name/${configFile.key}/$field")
            }
        }
    }


    fun findRouteAnnotaionHandlers(applicationFiles: List<AuroraConfigFile>): List<AuroraConfigFieldHandler> {

        return applicationFiles.flatMap { ac ->
            ac.contents.at("/route/annotations")?.fieldNames()?.asSequence()?.toList() ?: emptyList()
        }.toSet().map { key ->
            AuroraConfigFieldHandler("route/annotations/$key")
        }
    }

    fun findMounts(applicationFiles: List<AuroraConfigFile>): List<AuroraConfigFieldHandler> {

        val mountKeys = findSubKeys(applicationFiles, "mounts")

        return mountKeys.flatMap { mountName ->
            listOf(
                    AuroraConfigFieldHandler("mounts/$mountName/path", validator = { it.required("Path is required for mount") }),
                    AuroraConfigFieldHandler("mounts/$mountName/type", validator = { it.oneOf(MountType.values().map { it.name }) }),
                    AuroraConfigFieldHandler("mounts/$mountName/mountName", defaultValue = mountName),
                    AuroraConfigFieldHandler("mounts/$mountName/volumeName", defaultValue = mountName),
                    AuroraConfigFieldHandler("mounts/$mountName/exist", defaultValue = "false"),
                    AuroraConfigFieldHandler("mounts/$mountName/content"),
                    AuroraConfigFieldHandler("mounts/$mountName/secretFolder")
            )

        }
    }

    fun findParameters(applicationFiles: List<AuroraConfigFile>): List<AuroraConfigFieldHandler> {

        val parameterKeys = findSubKeys(applicationFiles, "parameters")

        return parameterKeys.map { parameter ->
            AuroraConfigFieldHandler("parameters/$parameter")
        }
    }

    private fun findSubKeys(applicationFiles: List<AuroraConfigFile>, name: String): Set<String> {

        return applicationFiles.flatMap {
            if (it.contents.has(name)) {
                it.contents[name].fieldNames().asSequence().toList()
            } else {
                emptyList()
            }
        }.toSet()
    }

    private fun validateGroups(openShiftClient: OpenShiftClient, required: Boolean = true): (JsonNode?) -> Exception? {
        return { json ->
            if (required && (json == null || json.textValue().isBlank())) {
                IllegalArgumentException("Groups must be set")
            } else {
                val groups = json?.textValue()?.split(" ")?.toSet()
                groups?.filter { !openShiftClient.isValidGroup(it) }
                        .takeIf { it != null && it.isNotEmpty() }
                        ?.let { AuroraConfigException("The following groups are not valid=${it.joinToString()}") }
            }
        }
    }

    private fun validateUsers(openShiftClient: OpenShiftClient): (JsonNode?) -> AuroraConfigException? {
        return { json ->
            val users = json?.textValue()?.split(" ")?.toSet()
            users?.filter { !openShiftClient.isValidUser(it) }
                    .takeIf { it != null && it.isNotEmpty() }
                    ?.let { AuroraConfigException("The following users are not valid=${it.joinToString()}") }
        }
    }

    private fun validateSecrets(auroraConfig: AuroraConfig): (JsonNode?) -> Exception? {
        return { json ->

            val secretFolder = json?.textValue()
            val secrets = secretFolder?.let {
                auroraConfig.getSecrets(it)
            }

            if (secrets != null && secrets.isEmpty()) {
                IllegalArgumentException("No secrets in folder=$secretFolder")
            } else {
                null
            }
        }
    }
}