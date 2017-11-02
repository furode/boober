package no.skatteetaten.aurora.boober.model

import static no.skatteetaten.aurora.boober.model.ApplicationId.aid

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import spock.lang.Specification

abstract class AbstractAuroraConfigTest extends Specification {

  static final DEFAULT_ABOUT = '''{
  "schemaVersion": "v1",
  "permissions": {
    "admin": "APP_PaaS_utv"
  },
  "affiliation" : "aos"
}'''

  static final String DEFAULT_UTV_ABOUT = '''{
  "cluster": "utv"
}'''

  static final String AOS_SIMPLE_JSON = '''{
  "certificate": true,
  "groupId": "ske.aurora.openshift",
  "artifactId": "aos-simple",
  "name": "aos-simple",
  "version": "1.0.3",
  "route": true,
  "type": "deploy"
}'''

  static final String WEB_LEVERANSE = '''{
  "applicationPlatform" : "web",
  "name" : "webleveranse",
  "groupId" : "no.skatteetaten.aurora",
  "artifactId" : "openshift-referanse-react",
  "replicas" : 1,
  "deployStrategy" : {
    "type" : "rolling"
  },
  "route" : true,
  "management" : {
    "port" : "8081",
    "path" : ""
  }
}'''

  static defaultAuroraConfig() {
    [
        "about.json"         : DEFAULT_ABOUT,
        "utv/about.json"     : DEFAULT_UTV_ABOUT,
        "aos-simple.json"    : AOS_SIMPLE_JSON,
        "utv/aos-simple.json": '''{ }'''
    ]
  }

  static final DEFAULT_AID = aid("utv", "aos-simple")

  static AuroraConfig createAuroraConfig(Map<String, String> auroraConfigJson) {
    def objectMapper = new ObjectMapper()
    def auroraConfigFiles = auroraConfigJson.collect { name, contents ->
      new AuroraConfigFile(name, objectMapper.readValue(contents, JsonNode), false, null)
    }
    def auroraConfig = new AuroraConfig(auroraConfigFiles, "aos")
    auroraConfig
  }

  static modify(Map<String, String> auroraConfig, String fileName, Closure modifier) {
    def configFile = auroraConfig[fileName]
    def asJson = new JsonSlurper().parseText(configFile)
    modifier.resolveStrategy = Closure.DELEGATE_FIRST
    modifier.delegate = asJson
    modifier()
    def modifiedJson = JsonOutput.prettyPrint(JsonOutput.toJson(asJson))
    auroraConfig[fileName] = modifiedJson
  }
}