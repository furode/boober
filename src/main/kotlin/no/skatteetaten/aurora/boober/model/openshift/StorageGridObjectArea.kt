package no.skatteetaten.aurora.boober.model.openshift

import com.fasterxml.jackson.annotation.*
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.ObjectMeta
import no.skatteetaten.aurora.boober.model.openshift.StorageGridObjectAreaStatusResult.Companion.defaultReason
import no.skatteetaten.aurora.boober.model.openshift.StorageGridObjectAreaStatusResult.Reason

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder(value = ["apiVersion", "kind", "metadata", "spec"])
data class StorageGridObjectArea(
    val spec: StorageGridObjectAreaSpec,
    val status: StorageGridObjectAreaStatus? = null,

    @JsonIgnore
    var _metadata: ObjectMeta?,
    @JsonIgnore
    val _kind: String = "StorageGridObjectArea",
    @JsonIgnore
    var _apiVersion: String = "skatteetaten.no/v1"
) : HasMetadata {
    override fun getMetadata() = _metadata ?: ObjectMeta()

    override fun getKind() = _kind

    override fun getApiVersion() = _apiVersion

    override fun setMetadata(metadata: ObjectMeta?) {
        _metadata = metadata
    }

    override fun setApiVersion(version: String?) {
        _apiVersion = apiVersion
    }
}

val StorageGridObjectArea.fqn get() = _metadata?.run { "${namespace}/${name}" } ?: "unknown"

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class StorageGridObjectAreaSpec(
    var bucketPostfix: String,
    var applicationDeploymentId: String,
    var objectArea: String
)

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class StorageGridObjectAreaStatus(
    var result: StorageGridObjectAreaStatusResult,
    var retryCount: Int?
)

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class StorageGridObjectAreaStatusResult(
    var message: String = "",
    @JsonProperty("reason")
    var reasonString: String = defaultReason.toString(),
    var success: Boolean = false
) {
    enum class Reason(val endState: Boolean = false) {
        @JsonEnumDefaultValue
        Undefined,
        SGOAProvisioned(true),
        AdIdNotBelongToNamespace(true),
        ApplicationDeploymentDoesNotExist(true),
        TenantAccountDoesNotExist(true),
        FailedToProvisionObjectArea,
        SpecConsistencyValidationFailure(true),
        FailureFromHerkimer,
        InternalError(true)
    }

    companion object {
        val defaultReason get() = Reason.Undefined
    }
}

val StorageGridObjectAreaStatusResult.reason
    get() = if (reasonString.isBlank()) defaultReason
    else Reason.valueOf(reasonString)

