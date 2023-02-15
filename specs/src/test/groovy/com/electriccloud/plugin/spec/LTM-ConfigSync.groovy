package com.electriccloud.plugin.spec

import com.electriccloud.plugins.annotations.Sanity
import spock.lang.*

//@Ignore
@Stepwise
class LTMConfigSync extends PluginTestHelper {
    static final String procedureName = "LTM - Config sync"
    static final String projectName = "Spec Tests: $procedureName"
    static final String configName = randomize("specs_config_" + procedureName.replaceAll(' ', '_'))

    static BigIpClient helper

    static resultPropertyBase = '/myJob/'
    static resultPropertyPath = 'configSync'
    static resultPropertySheet = resultPropertyBase + resultPropertyPath

    static partition = 'Common'
    static deviceGroup = 'first_group'

    @Shared
    String caseId

    def doSetupSpec() {
        helper = getBigIpHelper()

        helper.createDeviceGroup(partition, deviceGroup)

        createConfiguration(configName)
        dslFile "dsl/RunProcedure.dsl", [projectName: projectName, resName: 'local', procedureName: procedureName, params: configSyncParams]

    }

    def doCleanupSpec() {
        deleteConfiguration(PLUGIN_NAME, configName)
        conditionallyDeleteProject(projectName)

        helper.deleteDeviceGroup(partition, deviceGroup)
    }

    @Sanity
    @Unroll
    def '#caseId. LTM - Config sync'() {
        given:
        def procedureParams = [
            config             : configName,
            deviceGroup        : devGrp,
            resultPropertySheet: resultPropertySheet,
        ]

        when:
        def result = runProcedure(projectName, procedureName, procedureParams)

        then:
        logger.debug('#001: '+getJobLink(result.jobId))
        assert result.outcome == 'success'

        // Get result property
        def properties = getJobProperties(result.jobId)
        logger.debug('#002: '+objectToJson(properties))
        assert properties['resultPropertySheet']

        where:
        caseId | devGrp
        'good' | 'first_group'
    }
}
