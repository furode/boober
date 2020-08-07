package no.skatteetaten.aurora.boober.feature

import com.fkorotkov.kubernetes.metadata
import com.fkorotkov.kubernetes.newNamespace
import com.fkorotkov.kubernetes.rbac.metadata
import com.fkorotkov.kubernetes.rbac.newRoleBinding
import com.fkorotkov.kubernetes.rbac.newSubject
import com.fkorotkov.kubernetes.rbac.roleRef
import io.fabric8.kubernetes.api.model.Namespace
import io.fabric8.kubernetes.api.model.rbac.RoleBinding
import mu.KotlinLogging
import no.skatteetaten.aurora.boober.model.AuroraConfigFieldHandler
import no.skatteetaten.aurora.boober.model.AuroraContextCommand
import no.skatteetaten.aurora.boober.model.AuroraDeploymentSpec
import no.skatteetaten.aurora.boober.model.AuroraResource
import no.skatteetaten.aurora.boober.service.AuroraDeploymentSpecValidationException
import no.skatteetaten.aurora.boober.service.AzureService
import no.skatteetaten.aurora.boober.service.UserDetailsProvider
import no.skatteetaten.aurora.boober.utils.Instants
import no.skatteetaten.aurora.boober.utils.addIfNotNull
import no.skatteetaten.aurora.boober.utils.normalizeLabels
import org.springframework.boot.convert.DurationStyle
import org.springframework.stereotype.Service
import java.time.Duration

private val logger = KotlinLogging.logger { }

val AuroraDeploymentSpec.envTTL: Duration?
    get() = this.getOrNull<String>("env/ttl")?.let {
        DurationStyle.SIMPLE.parse(
            it
        )
    }

data class Permissions(
    val admin: Permission,
    val view: Permission? = null
)

data class Permission(
    val groups: Set<String>?,
    val users: Set<String> = emptySet()
)

@Service
class EnvironmentFeature(
    val userDetailsProvider: UserDetailsProvider,
    val azureService: AzureService
) : Feature {
    override fun handlers(header: AuroraDeploymentSpec, cmd: AuroraContextCommand): Set<AuroraConfigFieldHandler> {
        return setOf()
    }

    override fun generate(adc: AuroraDeploymentSpec, cmd: AuroraContextCommand): Set<AuroraResource> {

        val rolebindings = generateRolebindings(adc).map {
            generateResource(it, header = true)
        }.toSet()
        return setOf(

            generateResource(
                generateNamespace(adc),
                header = true
            )
        ).addIfNotNull(rolebindings).toSet()
    }

    fun generateNamespace(adc: AuroraDeploymentSpec): Namespace {
        val ttl = adc.envTTL?.let {
            val removeInstant = Instants.now + it
            "removeAfter" to removeInstant.epochSecond.toString()
        }

        return newNamespace {
            metadata {
                labels = mapOf("affiliation" to adc.affiliation).addIfNotNull(ttl).normalizeLabels()
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

    fun generateRolebindings(adc: AuroraDeploymentSpec): List<RoleBinding> {

        val permissions = extractPermissions(adc)

        val admin = createRoleBinding("admin", permissions.admin, adc.namespace)

        val view = permissions.view?.let {
            createRoleBinding("view", it, adc.namespace)
        }

        return listOf(admin).addIfNotNull(view)
    }

    override fun validate(
        adc: AuroraDeploymentSpec,
        fullValidation: Boolean,
        cmd: AuroraContextCommand
    ): List<Exception> {

        val errors: List<Exception> = try {
            validateAdminGroups(adc)
            emptyList()
        } catch (e: Exception) {
            listOf(e)
        }

        if (!fullValidation) return errors

        val authenticatedUser = userDetailsProvider.getAuthenticatedUser()

        val permissions = extractPermissions(adc)
        val userNotInAdminUsers = !permissions.admin.users.contains(authenticatedUser.username)
        val adminGroups = permissions.admin.groups
        val userNotInAnyAdminGroups = !authenticatedUser.hasAnyRole(adminGroups)

        if (userNotInAdminUsers && userNotInAnyAdminGroups) {
            return errors.addIfNotNull(IllegalArgumentException("User=${authenticatedUser.fullName} does not have access to admin this environment from the groups=$adminGroups"))
        }
        return errors
    }

    private fun validateAdminGroups(adc: AuroraDeploymentSpec) {
        val permissions = extractPermissions(adc)

        val adminGroups: Set<String> = permissions.admin.groups ?: setOf()
        if (adminGroups.isEmpty()) {
            throw AuroraDeploymentSpecValidationException("permissions.admin cannot be empty")
        }

        val groupInfo = adminGroups.associateWith {
            azureService.fetchGroupInfo(it)
        }

        val nonExistantDeclaredGroups = adminGroups.filter { groupInfo[it] == null }
        if (nonExistantDeclaredGroups.isNotEmpty()) {
            throw AuroraDeploymentSpecValidationException("$nonExistantDeclaredGroups are not valid groupNames")
        }

        val adminGroupsAreNotEmpty = groupInfo.values.filterNotNull().any {
            it.hasMembers
        }

        if (!adminGroupsAreNotEmpty) {
            throw AuroraDeploymentSpecValidationException("All groups=[${adminGroups.joinToString(", ")}] are empty")
        }
    }

    fun createRoleBinding(
        rolebindingName: String,
        permission: Permission,
        rolebindingNamespace: String
    ): RoleBinding {

        return newRoleBinding {
            metadata {
                name = rolebindingName
                namespace = rolebindingNamespace
            }

            val userRefeerences = permission.users.map {
                newSubject {
                    kind = "User"
                    name = it
                }
            }
            val groupRefeerences = permission.groups?.map {
                newSubject {
                    kind = "Group"
                    name = azureService.fetchGroupInfo(it)?.id
                }
            }

            subjects = userRefeerences.addIfNotNull(groupRefeerences)

            roleRef {
                kind = "ClusterRole"
                name = rolebindingName
            }
        }
    }
}
