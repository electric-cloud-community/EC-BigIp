package com.electriccloud.plugin.spec

import com.electriccloud.plugins.annotations.*
import spock.lang.*

//@Ignore
class LTMConfigSyncTests extends PluginTestHelper{

    @Shared
    def procedureName = "LTM - Config sync",
        projectName = "TestProject: ConfigSync",
        configurationName = randomize("specs_config_" + procedureName.replaceAll(' ', '_')),
        helper,

        TC = [
                C387049:  [id: 'C387049', description: 'Run for Sync-Failover device group'],
                C387050:  [id: 'C387050', description: 'Run for Sync-Only device group'],
                //Negative TC
                C387051:  [id: 'C387051', description: 'Run with empty Configuration Name'],
                C387052:  [id: 'C387052', description: 'Run with empty Device Group Name'],
                C387053:  [id: 'C387053', description: 'Run with empty Result Property Sheet'],
                C387054:  [id: 'C387054', description: 'Run for non-exist Configuration Name'],
                C387055:  [id: 'C387055', description: 'Run for non-exist Device Group Name'],
                C387056:  [id: 'C387056', description: 'Run for non-exist Result Property Sheet'],
        ],
        expectedSummaries = [
                 synced        : 'Device group "GROUP" has been synced',
                 emptyConfig   : 'No config name',
                 emptyGroup    : 'Required parameter "deviceGroup" is missing',
                 emptyResult   : 'Required parameter "resultPropertySheet" is missing',
                 nonexisConfig : 'Configuration "configNonExist" does not exist',
                 nonexisGroup  : 'Configuration error: Device group (GROUP) not found in device group sync',
                 wrongResult   : 'Unrecognized path element in \'/sheetNonExist\': \'sheetNonExist\''
        ],
        expectedLogs = [
                 sync : "POST ${bigIpProtocol}://${bigIpHost}:${bigIpPort}/mgmt/tm/cm"
        ],
        outputParams = [
                sync : '{"kind":"tm:cm:runstate","utilCmdArgs":"config-sync to-group GROUP","command":"run"}'
        ],
        outputErrors = [
                 nonExistGroup : '{"code":400,"message":"01070734:3: Configuration error: Device group (GROUP) not found in device group sync","errorStack":[],"apiError":3}',
        ]


    def doSetupSpec() {
        helper = getBigIpHelper()
        createConfiguration(configurationName)
        dslFile "dsl/RunProcedure.dsl", [projectName: projectName, resName: 'local', procedureName: procedureName, params: configSyncParams]
    }

    def doCleanupSpec() {
        conditionallyDeleteProject(projectName)
        deleteConfiguration(PLUGIN_NAME, configurationName)
    }

    @Sanity
    @Unroll
    def 'Config sync: Sanity Positive #caseId.id #caseId.description'(){
        helper.createDeviceGroup(partition, deviceGroup)
        given: "Tests parameters for procedure LTM - Config sync"
        def procedureParams = [
                config: configName,
                deviceGroup: deviceGroup,
                resultPropertySheet: resultPropertySheet
        ]

        when: "Run procedure RunImageScan"
        def result = runProcedure(projectName, procedureName, procedureParams)
        def jobSummary = getJobProperty("/myJob/jobSteps/$procedureName/summary", result.jobId)

        def outputParameters = getJobOutputParameters(result.jobId, 1)

        def jobProperties = getJobProperties(result.jobId)

        then: "Verify results"
        verifyAll {
            result.outcome == 'success'

            assert jobProperties

            jobSummary.contains(expectedSummary.replace('GROUP',deviceGroup))

            result.logs.contains(expectedLog.replace('GROUP',deviceGroup))

            outputParameters.configSync == expectedOutput.replace('GROUP',deviceGroup)
        }

        cleanup:
        try {
            helper.deleteDeviceGroup(partition, deviceGroup)
        } catch (Throwable e) {
            logger.debug(e.message)
        }

        where:
        caseId     | configName        | partition | deviceGroup     | resultPropertySheet | expectedSummary          | expectedLog       | expectedOutput
        TC.C387049 | configurationName | 'Common'  | 'syncFailoverDG'| '/myJob/configSync' | expectedSummaries.synced | expectedLogs.sync | outputParams.sync
    }

    @NewFeature(pluginVersion = "3.0.0")
    @Unroll
    def 'Config sync: Positive #caseId.id #caseId.description'(){
        helper.createDeviceGroup(partition, deviceGroup)
        given: "Tests parameters for procedure LTM - Config sync"
        def procedureParams = [
                config: configName,
                deviceGroup: deviceGroup,
                resultPropertySheet: resultPropertySheet
        ]

        when: "Run procedure RunImageScan"
        def result = runProcedure(projectName, procedureName, procedureParams)
        def jobSummary = getJobProperty("/myJob/jobSteps/$procedureName/summary", result.jobId)

        def outputParameters = getJobOutputParameters(result.jobId, 1)

        def jobProperties = getJobProperties(result.jobId)

        then: "Verify results"
        verifyAll {
            result.outcome == 'success'

            assert jobProperties

            jobSummary.contains(expectedSummary.replace('GROUP',deviceGroup))

            result.logs.contains(expectedLog.replace('GROUP',deviceGroup))

            outputParameters.configSync == expectedOutput.replace('GROUP',deviceGroup)
        }

        cleanup:
        try {
            helper.deleteDeviceGroup(partition, deviceGroup)
        } catch (Throwable e) {
            logger.debug(e.message)
        }

        where:
        caseId     | configName        | partition | deviceGroup     | resultPropertySheet | expectedSummary          | expectedLog       | expectedOutput
        TC.C387049 | configurationName | 'Common'  | 'syncFailoverDG'| '/myJob/configSync' | expectedSummaries.synced | expectedLogs.sync | outputParams.sync
        TC.C387050 | configurationName | 'Common'  | 'syncOnlyDG'    | '/myJob/configSync' | expectedSummaries.synced | expectedLogs.sync | outputParams.sync
    }

    @NewFeature(pluginVersion = "3.0.0")
    @Unroll
    def 'Config sync: Negative #caseId.id #caseId.description'(){
        if ( partition && deviceGroup && (deviceGroup != 'DGNonExist') ){
            helper.createDeviceGroup(partition, deviceGroup)
        }

        given: "Tests parameters for procedure LTM - Config sync"
        def procedureParams = [
                config: configName,
                deviceGroup: deviceGroup,
                resultPropertySheet: resultPropertySheet
        ]

        when: "Run procedure RunImageScan"
        def result = runProcedure(projectName, procedureName, procedureParams)
        def jobSummary = getJobProperty("/myJob/jobSteps/$procedureName/summary", result.jobId)

        def outputParameters = getJobOutputParameters(result.jobId, 1)

        def jobProperties = getJobProperties(result.jobId)

        def propertyName
        if (resultPropertySheet && (resultPropertySheet != '/sheetNonExist')) {
            propertyName = jobProperties[resultPropertySheet.split("/")[2]]
        }

        then: "Verify results"
        verifyAll {
            result.outcome == 'error'

            jobSummary.contains(expectedSummary.replace('GROUP',deviceGroup))

            result.logs.contains(expectedLog.replace('GROUP',deviceGroup))

            if (error){
//                outputParameters.configSync == error.replace('GROUP',deviceGroup)
                assert outputParameters.configSync == null
//                propertyName == error.replace('GROUP',deviceGroup)
                assert propertyName == null
            }
            else {
                assert !outputParameters
                assert !propertyName
            }
        }

        cleanup:
        try {
            helper.deleteDeviceGroup(partition, deviceGroup)
        } catch (Throwable e) {
            logger.debug(e.message)
        }

        where:
        caseId     | configName        | partition  | deviceGroup     | resultPropertySheet | expectedSummary                     | expectedLog                     | error
        TC.C387051 | ''                | 'Common'   | 'syncFailoverDG'| '/myJob/configSync' | expectedSummaries.emptyConfig       | expectedSummaries.emptyConfig   | ''
        TC.C387052 | configurationName | 'Common'   | ''              | '/myJob/configSync' | expectedSummaries.emptyGroup        | expectedSummaries.emptyGroup    | ''
        TC.C387053 | configurationName | 'Common'   | 'syncFailoverDG'| ''                  | expectedSummaries.emptyResult       | expectedSummaries.emptyResult   | ''
        TC.C387054 | 'configNonExist'  | 'Common'   | 'syncFailoverDG'| '/myJob/configSync' | expectedSummaries.nonexisConfig     | expectedSummaries.nonexisConfig | ''
        TC.C387055 | configurationName | 'Common'   | 'DGNonExist'    | '/myJob/configSync' | expectedSummaries.nonexisGroup      | expectedLogs.sync               | outputErrors.nonExistGroup
        TC.C387056 | configurationName | 'Common'   | 'syncFailoverDG'| '/sheetNonExist'    | expectedSummaries.wrongResult       | expectedLogs.sync               | ''
    }
}
