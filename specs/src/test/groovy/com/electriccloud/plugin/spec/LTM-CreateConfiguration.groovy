package com.electriccloud.plugin.spec

import com.electriccloud.plugins.annotations.Sanity
import spock.lang.*

//@Ignore
@Stepwise
class LTMCreateConfiguration extends PluginTestHelper {
    static final String procedureName = "CreateConfiguration"
    static final String projectName = "Spec Tests: $procedureName"
    static final String configName = randomize("specs_config_" + procedureName.replaceAll(' ', '_'))

    static BigIpClient helper

    static resultPropertyBase = '/myJob/'
    static resultPropertyPath = 'configSync'

    @Shared
    String caseId
    String authenticationType
    String authenticationProvider

    def doSetupSpec() {
    }

    def doCleanupSpec() {
        conditionallyDeleteProject(projectName)
    }

    @Sanity
    @Unroll
    def '#caseId. CreateConfiguration Local'() {

        when:
        def result = createConfiguration(
            configName + '_' + caseId,
            [:],
            [
                authenticationType: authenticationType,
                authenticationProvider: authenticationProvider,
                attemptConnection: '1'
            ]
        )

        then:
        logger.debug('#001: '+getJobLink(result))

        cleanup:
        deleteConfiguration(PLUGIN_NAME, configName + '_' + caseId)

        where:
        caseId   | authenticationType | authenticationProvider
        'basic1' | 'basic'            | ''
        'basic2' | 'basic'            | 'local'
        'basic3' | 'basic'            | 'tmos'
        'tba1'   | 'tba'              | ''
        'tba2'   | 'tba'              | 'local'
        'tba3'   | 'tba'              | 'tmos'
    }

    @Sanity
    @Unroll
    @Requires({env.BIGIP_AUTHENTICATION_PROVIDER == 'tmos'})
    def '#caseId. CreateConfiguration Radius'() {

        when:
        def result = createConfiguration(
            configName + '_' + caseId,
            [:],
            [
                protocol: BIGIP_RADIUS_PROTOCOL,
                host: BIGIP_RADIUS_HOST,
                port: BIGIP_RADIUS_PORT,
                urlPath: BIGIP_RADIUS_URL_PATH,
                authenticationType: authenticationType,
                authenticationProvider: authenticationProvider,
                attemptConnection: '1'
            ]
        )

        then:
        logger.debug('#002: '+getJobLink(result))

        cleanup:
        deleteConfiguration(PLUGIN_NAME, configName + '_' + caseId)

        where:
        caseId  | authenticationType | authenticationProvider
        'basic1' | 'basic'            | ''
        'basic2' | 'basic'            | 'tmos'
        'tba1'   | 'tba'              | ''
        'tba2'   | 'tba'              | 'tmos'
    }
}
