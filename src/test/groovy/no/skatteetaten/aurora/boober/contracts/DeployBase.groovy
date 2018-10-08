package no.skatteetaten.aurora.boober.contracts

import no.skatteetaten.aurora.boober.controller.v1.DeployControllerV1
import no.skatteetaten.aurora.boober.mapper.AuroraDeploymentSpec
import no.skatteetaten.aurora.boober.model.ApplicationDeploymentRef
import no.skatteetaten.aurora.boober.model.AuroraConfigFile
import no.skatteetaten.aurora.boober.model.AuroraDeployEnvironment
import no.skatteetaten.aurora.boober.model.AuroraDeploymentSpecInternal
import no.skatteetaten.aurora.boober.model.AuroraTemplate
import no.skatteetaten.aurora.boober.model.Permission
import no.skatteetaten.aurora.boober.model.Permissions
import no.skatteetaten.aurora.boober.model.TemplateType
import no.skatteetaten.aurora.boober.model.openshift.ApplicationDeploymentCommand
import no.skatteetaten.aurora.boober.service.AuroraConfigRef
import no.skatteetaten.aurora.boober.service.AuroraDeployResult
import no.skatteetaten.aurora.boober.service.DeployService

class DeployBase extends AbstractContractBase {

  void setup() {
    loadJsonResponses(this)
    def deployService = Mock(DeployService) {
      executeDeploy(_ as AuroraConfigRef, _ as List, _ as List, _ as Boolean) >> {
        arguments ->
          (arguments[0].name == 'invalid') ? [createAuroraDeployResult(false)] : [createAuroraDeployResult(true)]
      }
    }
    DeployControllerV1 controller = new DeployControllerV1(deployService)
    setupMockMvc(controller)
  }

  AuroraDeployResult createAuroraDeployResult(Boolean success) {
    String reason
    if (success) {
      reason = response('deploy', '$.message', String)
    } else {
      reason = response('deploy-failed', '$.message', String)
    }

    def spec = new AuroraDeploymentSpecInternal(new ApplicationDeploymentRef('', ''), '', TemplateType.development, '',
        new AuroraDeploymentSpec([:]), '', '',
        new AuroraDeployEnvironment('', '',
            new Permissions(new Permission(Collections.emptySet(), Collections.emptySet()), null), null),
        null, null, null, null, new AuroraTemplate([:], 'test', '1.0.0', 1), null, null,
        new AuroraConfigFile("", "{}", false, false), "master", [:])

    def command = new ApplicationDeploymentCommand([:], new ApplicationDeploymentRef("", ""),
        new AuroraConfigRef("", "", ""))

    return new AuroraDeployResult(command, spec, UUID.randomUUID().toString().substring(0, 7), [], success, false,
        reason)
  }
}
