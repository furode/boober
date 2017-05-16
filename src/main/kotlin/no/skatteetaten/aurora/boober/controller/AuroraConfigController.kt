package no.skatteetaten.aurora.boober.controller

import com.fasterxml.jackson.databind.JsonNode
import no.skatteetaten.aurora.boober.model.AuroraConfig
import no.skatteetaten.aurora.boober.service.AuroraConfigService
import no.skatteetaten.aurora.boober.service.AuroraDeploymentConfigService
import org.springframework.util.AntPathMatcher
import org.springframework.web.bind.annotation.*
import javax.servlet.http.HttpServletRequest


@RestController
@RequestMapping("/affiliation")
class AuroraConfigController(val auroraConfigService: AuroraConfigService,
                             val auroraDeploymentConfigService: AuroraDeploymentConfigService) {

    @PutMapping("/{affiliation}/auroraconfig")
    fun save(@PathVariable affiliation: String, @RequestBody payload: AuroraConfigPayload) {

        val auroraConfig = payload.toAuroraConfig()

        auroraDeploymentConfigService.validate(auroraConfig)
        auroraConfigService.save(affiliation, auroraConfig)
    }

    @GetMapping("/{affiliation}/auroraconfig")
    fun get(@PathVariable affiliation: String): Response {

        return Response(items = listOf(auroraConfigService.findAuroraConfig(affiliation)).map(::fromAuroraConfig))
    }

    @PutMapping("/{affiliation}/auroraconfig/{environment}/{filename:\\w+}.json")
    fun updateAuroraConfigFile(@PathVariable affiliation: String, @PathVariable environment: String,
                                  @PathVariable filename: String, @RequestBody fileContents: JsonNode) {

        auroraConfigService.updateAuroraConfigFile(affiliation, "$environment/$filename.json", fileContents)
    }

    @PutMapping("/{affiliation}/auroraconfig/{filename:\\w+}.json")
    fun updateAuroraConfigFile(@PathVariable affiliation: String, @PathVariable filename: String,
                               @RequestBody fileContents: JsonNode) {

        auroraConfigService.updateAuroraConfigFile(affiliation, "$filename.json", fileContents)
    }

    @PatchMapping(value = "/{affiliation}/auroraconfig/**", consumes = arrayOf("application/json-patch+json"))
    fun patchAuroraConfigFile(@PathVariable affiliation: String, request: HttpServletRequest,
                              @RequestBody jsonPatchOp: String) {

        val path = "affiliation/$affiliation/auroraconfig/**"
        val fileName = AntPathMatcher().extractPathWithinPattern(path, request.requestURI)

        auroraConfigService.withAuroraConfig(affiliation, true, { auroraConfig: AuroraConfig ->
            val fileContents = auroraConfigService.patchAuroraConfigFile(fileName, auroraConfig, jsonPatchOp)
            auroraConfig.updateFile(fileName, fileContents)
        })
    }

}


