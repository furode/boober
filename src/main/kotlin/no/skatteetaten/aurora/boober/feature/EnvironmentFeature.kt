package no.skatteetaten.aurora.boober.feature

import com.fkorotkov.kubernetes.metadata
import com.fkorotkov.kubernetes.newNamespace
import com.fkorotkov.openshift.metadata
import com.fkorotkov.openshift.newProjectRequest
import io.fabric8.kubernetes.api.model.Namespace
import io.fabric8.openshift.api.model.OpenshiftRoleBinding
import io.fabric8.openshift.api.model.ProjectRequest
import no.skatteetaten.aurora.boober.mapper.*
import no.skatteetaten.aurora.boober.service.AuroraDeploymentSpecValidationException
import no.skatteetaten.aurora.boober.service.AuroraResource
import no.skatteetaten.aurora.boober.service.Feature
import no.skatteetaten.aurora.boober.service.internal.RolebindingGenerator
import no.skatteetaten.aurora.boober.service.openshift.OpenShiftClient
import no.skatteetaten.aurora.boober.utils.Instants
import no.skatteetaten.aurora.boober.utils.addIfNotNull
import org.springframework.boot.convert.DurationStyle
import org.springframework.stereotype.Service
import java.time.Duration

val AuroraDeploymentSpec.envTTL: Duration? get() = this.getOrNull<String>("env/ttl")?.let { DurationStyle.SIMPLE.parse(it) }

// TODO: Add and
@Service
class EnvironmentFeature(val openShiftClient: OpenShiftClient) : Feature {
    override fun handlers(header: AuroraDeploymentSpec, cmd: AuroraDeploymentCommand): Set<AuroraConfigFieldHandler> {
        return setOf()
    }

    override fun generate(adc: AuroraDeploymentSpec, cmd: AuroraDeploymentCommand): Set<AuroraResource> {

        val rolebindings = generateRolebindings(adc).map {
            AuroraResource("${it.metadata.name}-${it.kind}", it, header = true)
        }.toSet()
        return setOf(
                AuroraResource("${adc.namespace}-prj", generateProjectRequest(adc), header = true),
                AuroraResource("${adc.namespace}-ns", generateNamespace(adc), header = true)
        ).addIfNotNull(rolebindings).toSet()
    }

    fun generateNamespace(adc: AuroraDeploymentSpec): Namespace {
        val ttl = adc.envTTL?.let {
            val removeInstant = Instants.now + it
            "removeAfter" to removeInstant.epochSecond.toString()
        }

        return newNamespace {
            metadata {
                labels = mapOf("affiliation" to adc.affiliation).addIfNotNull(ttl)
                name = adc.namespace
            }
        }
    }

    fun generateProjectRequest(adc: AuroraDeploymentSpec): ProjectRequest {

        return newProjectRequest {
            metadata {
                name = adc.namespace
            }
        }

    }

    fun extractPermissions(deploymentSpec: AuroraDeploymentSpec): Permissions {

        val viewGroups = deploymentSpec.getDelimitedStringOrArrayAsSet("permissions/view", " ")
        val adminGroups = deploymentSpec.getDelimitedStringOrArrayAsSet("permissions/admin", " ")
        // if sa present add to admin users.
        val adminUsers = deploymentSpec.getDelimitedStringOrArrayAsSet("permissions/adminServiceAccount", " ")

        val adminPermission = Permission(adminGroups, adminUsers)
        val viewPermission = if (viewGroups.isNotEmpty()) Permission(viewGroups) else null

        return Permissions(admin = adminPermission, view = viewPermission)
    }

    fun generateRolebindings(adc: AuroraDeploymentSpec): List<OpenshiftRoleBinding> {

        val permissions = extractPermissions(adc)

        val admin = RolebindingGenerator.create("admin", permissions.admin, adc.namespace)

        val view = permissions.view?.let {
            RolebindingGenerator.create("view", it, adc.namespace)
        }

        return listOf(admin).addIfNotNull(view)
    }

    override fun validate(adc: AuroraDeploymentSpec, fullValidation: Boolean, cmd: AuroraDeploymentCommand): List<Exception> {
        if (!fullValidation) return emptyList()

        return try {
            validateAdminGroups(adc)
            emptyList()
        } catch (e: Exception) {
            listOf(e)
        }
    }

    private fun validateAdminGroups(adc: AuroraDeploymentSpec) {
        val permissions = extractPermissions(adc)

        val adminGroups: Set<String> = permissions.admin.groups ?: setOf()
        adminGroups.takeIf { it.isEmpty() }
                ?.let { throw AuroraDeploymentSpecValidationException("permissions.admin.groups cannot be empty") }

        val openShiftGroups = openShiftClient.getGroups()

        val nonExistantDeclaredGroups = adminGroups.filter { !openShiftGroups.groupExist(it) }
        if (nonExistantDeclaredGroups.isNotEmpty()) {
            throw AuroraDeploymentSpecValidationException("$nonExistantDeclaredGroups are not valid groupNames")
        }

        val sumMembers = adminGroups.sumBy {
            openShiftGroups.groupUsers[it]?.size ?: 0
        }

        if (0 == sumMembers) {
            throw AuroraDeploymentSpecValidationException("All groups=[${adminGroups.joinToString(", ")}] are empty")
        }
    }

}