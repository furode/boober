package no.skatteetaten.aurora.boober.feature

import com.fkorotkov.kubernetes.metadata
import com.fkorotkov.kubernetes.newEnvVar
import com.fkorotkov.kubernetes.newSecret
import com.fkorotkov.kubernetes.secretKeyRef
import com.fkorotkov.kubernetes.valueFrom
import io.fabric8.kubernetes.api.model.EnvVar
import no.skatteetaten.aurora.boober.model.AuroraConfigFieldHandler
import no.skatteetaten.aurora.boober.model.AuroraContextCommand
import no.skatteetaten.aurora.boober.model.AuroraDeploymentSpec
import no.skatteetaten.aurora.boober.model.AuroraResource
import no.skatteetaten.aurora.boober.model.addEnvVar
import no.skatteetaten.aurora.boober.model.findSubKeys
import no.skatteetaten.aurora.boober.service.AuroraDeploymentSpecValidationException
import no.skatteetaten.aurora.boober.service.resourceprovisioning.VaultProvider
import no.skatteetaten.aurora.boober.service.resourceprovisioning.VaultRequest
import no.skatteetaten.aurora.boober.service.resourceprovisioning.VaultSecretEnvResult
import no.skatteetaten.aurora.boober.utils.addIfNotNull
import no.skatteetaten.aurora.boober.utils.ensureStartWith
import no.skatteetaten.aurora.boober.utils.filterProperties
import no.skatteetaten.aurora.boober.utils.normalizeKubernetesName
import no.skatteetaten.aurora.boober.utils.takeIfNotEmpty
import org.apache.commons.codec.binary.Base64
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class SecretVaultFeature(
    val vaultProvider: VaultProvider,
    @Value("\${openshift.cluster}") val cluster: String
) : Feature {

    fun secretVaultKeyMappingHandlers(cmd: AuroraContextCommand) = cmd.applicationFiles.find {
        it.asJsonNode.at("/secretVault/keyMappings") != null
    }?.let {
        AuroraConfigFieldHandler("secretVault/keyMappings")
    }

    override fun handlers(header: AuroraDeploymentSpec, cmd: AuroraContextCommand): Set<AuroraConfigFieldHandler> {

        val secretVaultsHandlers = cmd.applicationFiles.findSubKeys("secretVaults").flatMap { key ->
            listOf(
                AuroraConfigFieldHandler(
                    "secretVaults/$key/name",
                    defaultValue = key
                ),
                AuroraConfigFieldHandler(
                    "secretVaults/$key/enabled",
                    defaultValue = true
                ),
                AuroraConfigFieldHandler(
                    "secretVaults/$key/file",
                    defaultValue = "latest.properties"
                ),
                AuroraConfigFieldHandler("secretVaults/$key/keys"),
                AuroraConfigFieldHandler("secretVaults/$key/keyMappings")
            )
        }
        return listOf(
            AuroraConfigFieldHandler("secretVault"),
            AuroraConfigFieldHandler("secretVault/name"),
            AuroraConfigFieldHandler("secretVault/keys")
        )
            .addIfNotNull(secretVaultKeyMappingHandlers(cmd))
            .addIfNotNull(secretVaultsHandlers)
            .toSet()
    }

    override fun validate(
        adc: AuroraDeploymentSpec,
        fullValidation: Boolean,
        cmd: AuroraContextCommand
    ): List<Exception> {
        if (!fullValidation) {
            return emptyList()
        }
        val secrets = getSecretVaults(adc, cmd)
        return validateVaultExistence(secrets, adc.affiliation)
            .addIfNotNull(validateSecretNames(secrets))
            .addIfNotNull(validateKeyMappings(secrets))
            .addIfNotNull(validateSecretVaultKeys(secrets, adc.affiliation))
            .addIfNotNull(validateSecretVaultFiles(secrets, adc.affiliation))
            .addIfNotNull(validateDuplicateSecretEnvNames(secrets))
    }

    fun validateVaultExistence(
        secrets: List<AuroraSecret>,
        vaultCollectionName: String
    ): List<AuroraDeploymentSpecValidationException> {

        return secrets.map { it.secretVaultName }
            .mapNotNull {
                if (!vaultProvider.vaultExists(vaultCollectionName, it)) {
                    AuroraDeploymentSpecValidationException("Referenced Vault $it in Vault Collection $vaultCollectionName does not exist")
                } else null
            }
    }

    fun validateSecretNames(secrets: List<AuroraSecret>): List<AuroraDeploymentSpecValidationException> {
        return secrets.mapNotNull { secret ->
            if (secret.name.length > 63) {
                AuroraDeploymentSpecValidationException("The name of the secretVault=${secret.name} is too long. Max 63 characters. Note that we ensure that the name starts with @name@-")
            } else null
        }
    }

    private fun validateKeyMappings(secrets: List<AuroraSecret>): List<AuroraDeploymentSpecValidationException> {
        return secrets.mapNotNull { validateKeyMapping(it) }
    }

    private fun validateKeyMapping(secret: AuroraSecret): AuroraDeploymentSpecValidationException? {
        val keyMappings = secret.keyMappings.takeIfNotEmpty() ?: return null
        val keys = secret.secretVaultKeys.takeIfNotEmpty() ?: return null
        val diff = keyMappings.keys - keys
        return if (diff.isNotEmpty()) {
            AuroraDeploymentSpecValidationException("The secretVault keyMappings $diff were not found in keys")
        } else null
    }

    /**
     * Validates that any secretVaultKeys specified actually exist in the vault.
     * Note that this method always uses the latest.properties file regardless of the version of the application and
     * the contents of the vault.
     */
    private fun validateSecretVaultKeys(
        secrets: List<AuroraSecret>,
        vaultCollection: String
    ): List<AuroraDeploymentSpecValidationException> {
        return secrets.mapNotNull {
            validateSecretVaultKey(it, vaultCollection)
        }
    }

    private fun validateSecretVaultKey(
        secret: AuroraSecret,
        vaultCollection: String
    ): AuroraDeploymentSpecValidationException? {
        val vaultName = secret.secretVaultName
        val keys = secret.secretVaultKeys.takeIfNotEmpty() ?: return null

        val vaultKeys = vaultProvider.findVaultKeys(vaultCollection, vaultName, secret.file)
        val missingKeys = keys - vaultKeys
        return if (missingKeys.isNotEmpty()) {
            throw AuroraDeploymentSpecValidationException("The keys $missingKeys were not found in the secret vault")
        } else null
    }

    private fun validateSecretVaultFiles(
        secrets: List<AuroraSecret>,
        vaultCollection: String
    ): List<AuroraDeploymentSpecValidationException> {
        return secrets.mapNotNull {
            validateSecretVaultFile(it, vaultCollection)
        }
    }

    private fun validateSecretVaultFile(
        secret: AuroraSecret,
        vaultCollectionName: String
    ): AuroraDeploymentSpecValidationException? {
        return try {
            vaultProvider.findFileInVault(
                vaultCollectionName = vaultCollectionName,
                vaultName = secret.secretVaultName,
                fileName = secret.file
            )
            null
        } catch (e: Exception) {
            AuroraDeploymentSpecValidationException("File with name=${secret.file} is not present in vault=${secret.secretVaultName} in collection=$vaultCollectionName")
        }
    }

    /*
     * Validates that the name property of a secret it unique
     */
    private fun validateDuplicateSecretEnvNames(secrets: List<AuroraSecret>): AuroraDeploymentSpecValidationException? {

        val secretNames = secrets.map { it.name }
        return if (secretNames.size != secretNames.toSet().size) {
            AuroraDeploymentSpecValidationException(
                "SecretVaults does not have unique names=[${secretNames.joinToString(
                    ", "
                )}]"
            )
        } else null
    }

    override fun generate(adc: AuroraDeploymentSpec, cmd: AuroraContextCommand): Set<AuroraResource> {

        val secretEnvResult = handleSecretEnv(adc, cmd)

        return secretEnvResult.map {
            val secret = newSecret {
                metadata {
                    name = it.name
                    namespace = adc.namespace
                }
                data = it.secrets.mapValues { Base64.encodeBase64String(it.value) }
            }
            generateResource(secret)
        }.toSet()
    }

    private fun handleSecretEnv(adc: AuroraDeploymentSpec, cmd: AuroraContextCommand): List<VaultSecretEnvResult> {
        val secrets = getSecretVaults(adc, cmd)
        return secrets.mapNotNull { secret: AuroraSecret ->
            val request = VaultRequest(
                collectionName = adc.affiliation,
                name = secret.secretVaultName,
                keys = secret.secretVaultKeys,
                keyMappings = secret.keyMappings
            )
            vaultProvider.findVaultDataSingle(request)[secret.file]?.let { file ->
                val properties = filterProperties(file, secret.secretVaultKeys, secret.keyMappings)
                properties.map {
                    it.key.toString() to it.value.toString().toByteArray()
                }
            }?.let {
                VaultSecretEnvResult(secret.secretVaultName.ensureStartWith(adc.name, "-"), it.toMap())
            }
        }
    }

    override fun modify(adc: AuroraDeploymentSpec, resources: Set<AuroraResource>, cmd: AuroraContextCommand) {

        val secretEnv: List<EnvVar> = handleSecretEnv(adc, cmd).flatMap { result ->
            result.secrets.map { secretValue ->
                newEnvVar {
                    name = secretValue.key
                    valueFrom {
                        secretKeyRef {
                            key = secretValue.key
                            name = result.name
                            optional = false
                        }
                    }
                }
            }
        }
        resources.addEnvVar(secretEnv, this::class.java)
    }

    private fun getSecretVaults(adc: AuroraDeploymentSpec, cmd: AuroraContextCommand): List<AuroraSecret> {
        val secret = getSecretVault(adc)?.let {
            AuroraSecret(
                secretVaultName = it,
                keyMappings = adc.getKeyMappings(secretVaultKeyMappingHandlers(cmd)),
                secretVaultKeys = getSecretVaultKeys(adc),
                file = "latest.properties",
                name = adc.name
            )
        }

        val secretVaults = cmd.applicationFiles.findSubKeys("secretVaults").mapNotNull {
            val enabled: Boolean = adc["secretVaults/$it/enabled"]

            if (!enabled) {
                null
            } else {
                AuroraSecret(
                    secretVaultKeys = adc.getOrNull("secretVaults/$it/keys") ?: listOf(),
                    keyMappings = adc.getOrNull("secretVaults/$it/keyMappings"),
                    file = adc["secretVaults/$it/file"],
                    name = adc.replacer.replace(it).ensureStartWith(adc.name, "-").normalizeKubernetesName(),
                    secretVaultName = adc["secretVaults/$it/name"]
                )
            }
        }

        return secretVaults.addIfNotNull(secret)
    }

    private fun getSecretVault(auroraDeploymentSpec: AuroraDeploymentSpec): String? =
        auroraDeploymentSpec.getOrNull("secretVault/name")
            ?: auroraDeploymentSpec.getOrNull("secretVault")

    private fun getSecretVaultKeys(auroraDeploymentSpec: AuroraDeploymentSpec): List<String> =
        auroraDeploymentSpec.getOrNull("secretVault/keys") ?: listOf()
}

data class AuroraSecret(
    val secretVaultName: String,
    val secretVaultKeys: List<String>,
    val keyMappings: Map<String, String>?,
    val file: String,
    val name: String

)
