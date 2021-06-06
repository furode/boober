package no.skatteetaten.aurora.boober.feature

import no.skatteetaten.aurora.boober.model.*
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty("integrations.operations.scope")
class OperationScopeFeature(
    @Value("\${integrations.operations.scope}") val operationScopeConfiguration: String
) : Feature {
    override fun handlers(header: AuroraDeploymentSpec, cmd: AuroraContextCommand): Set<AuroraConfigFieldHandler> =
        emptySet()

    override fun modify(
        adc: AuroraDeploymentSpec,
        resources: Set<AuroraResource>,
        context: FeatureContext
    ) {

        if (operationScopeConfiguration.isBlank()) {
            return
        }

        resources.addLabels(
            commonLabels = getLabelsToAdd(),
            comment = "Added operationScope label",
            clazz = this::class.java
        )
    }

    fun getLabelsToAdd() =
        if (operationScopeConfiguration.isBlank()) emptyMap()
        else mapOf("operationScope" to operationScopeConfiguration)
}
