package no.skatteetaten.aurora.boober.controller

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import no.skatteetaten.aurora.boober.controller.security.User
import no.skatteetaten.aurora.boober.service.OpenShiftClient
import no.skatteetaten.aurora.boober.utils.SampleFilesCollector

class SetupControllerTest extends AbstractControllerTest {

  public static final String AFFILIATION = "aos"
  public static final String ENV_NAME = "booberdev"
  public static final String APP_NAME = "verify-ebs-users"

  @Autowired
  OpenShiftClient openShiftClient

  def "Should fail when Aurora Config contains errors"() {
    given:
      def files = SampleFilesCollector.qaEbsUsersSampleFiles
      files.put("about.json", [:])
      SetupCommand cmd = new SetupCommand(AFFILIATION, ENV_NAME, APP_NAME, files, [:])
      def json = JsonOutput.toJson(cmd)

    when:
      def response = mockMvc
          .perform(put('/setup')
          .with(user(new User("test", "test", "Test User")))
          .contentType(MediaType.APPLICATION_JSON)
          .content(json))
      def body = new JsonSlurper().parseText(response.andReturn().response.getContentAsString())

    then:
      body.items.size() == 1
      response.andExpect(status().is4xxClientError())
  }
}