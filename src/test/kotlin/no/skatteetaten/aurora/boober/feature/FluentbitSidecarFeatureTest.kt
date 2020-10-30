package no.skatteetaten.aurora.boober.feature

import assertk.assertThat
import no.skatteetaten.aurora.boober.utils.AbstractFeatureTest
import org.junit.jupiter.api.Test

class FluentbitSidecarFeatureTest : AbstractFeatureTest() {
    override val feature: Feature
        get() = FluentbitSidecarFeature("test_hec", "splunk.url", "8080")

    @Test
    fun `should add fluentbit to dc`() {
        // mockVault("foo")
        val (dcResource, configResource, secretResource) = generateResources(
                """{
             "logging" : {
                "index": "test-index"             
             } 
           }""",
                createEmptyDeploymentConfig(), emptyList(), 2
        )
        assertThat(dcResource).auroraResourceModifiedByThisFeatureWithComment("Added fluentbit volume and sidecar container")
            .auroraResourceMatchesFile("dc.json")

        assertThat(configResource).auroraResourceCreatedByThisFeature().auroraResourceMatchesFile("config.json")

        assertThat(secretResource).auroraResourceCreatedByThisFeature().auroraResourceMatchesFile("secret.json")
    }

    @Test
    fun `should add fluentbit to dc using old splunkIndex`() {
        // mockVault("foo")
        val (dcResource, configResource, secretResource) = generateResources(
                """{
             "splunkIndex": "test-index"
           }""",
                createEmptyDeploymentConfig(), emptyList(), 2
        )
        assertThat(dcResource).auroraResourceModifiedByThisFeatureWithComment("Added fluentbit volume and sidecar container")
                .auroraResourceMatchesFile("dc.json")

        assertThat(configResource).auroraResourceCreatedByThisFeature().auroraResourceMatchesFile("config.json")

        assertThat(secretResource).auroraResourceCreatedByThisFeature().auroraResourceMatchesFile("secret.json")
    }
}
