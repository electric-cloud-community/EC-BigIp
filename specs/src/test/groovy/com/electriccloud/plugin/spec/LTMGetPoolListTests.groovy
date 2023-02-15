package com.electriccloud.plugin.spec

import com.electriccloud.plugins.annotations.*
import spock.lang.*

//@Ignore
class LTMGetPoolListTests extends PluginTestHelper {

    @Shared
    def procedureName = "LTM - Get pool list",
        projectName = "TestProject: Get pool list",
        numberPoolsOfList = 10,
        namePool = 'testPool',
        helper,

        TC = [
                C387258:  [id: 'C387258', description: 'Run with required fields'],
                //Negative TC
                C387259:  [id: 'C387259', description: 'Run with an empty configName'],
                C387260:  [id: 'C387260', description: 'Run with an empty resultPropertySheet'],
                C387261:  [id: 'C387261', description: 'Run with non-exist configName'],
                C387262:  [id: 'C387262', description: 'Run with /invalid resultPropertySheet']
        ],
        expectedSummaries = [
                getList           : 'Balancing pool list has been gotten',
                emptyConfig       : 'No config name',
                emptyResult       : 'Required parameter "resultPropertySheet" is missing',
                nonexisConfig     : 'Configuration "notExist" does not exist',
                wrongResult       : 'Unrecognized path element in \'/sheetNonExist\': \'sheetNonExist\''
        ],
        expectedLogs = [
                getList : "GET ${bigIpProtocol}://${bigIpHost}:${bigIpPort}/mgmt/tm/ltm/pool"
        ]

    def doSetupSpec() {
        helper = getBigIpHelper()
        createConfiguration(CONFIG_NAME)
        dslFile "dsl/RunProcedure.dsl", [projectName: projectName, resName: 'local', procedureName: procedureName, params: getPoolListParams]

        for (int i=0;i<numberPoolsOfList; i++){
            helper.createBalancingPool('Common',namePool+i)
        }
    }

    def doCleanupSpec() {
        conditionallyDeleteProject(projectName)
        deleteConfiguration(PLUGIN_NAME, CONFIG_NAME)
        for (int i=0;i<numberPoolsOfList; i++){
            try {
                helper.deleteBalancingPool('Common', namePool+i)
            } catch (Throwable e) {
                logger.debug(e.message)
            }
        }
    }

    @Sanity
    @Unroll
    def 'Get pool list: Sanity Positive #caseId.id #caseId.description'(){

        given: "Tests parameters for procedure LTM - Get pool list"

        def procedureParams = [
                config             : configName,
                resultPropertySheet:  resultPropertySheet,
        ]

        when: "LTM - Get pool list"
        def result = runProcedure(projectName, procedureName, procedureParams)
        def jobSummary = getJobProperty("/myJob/jobSteps/$procedureName/summary", result.jobId)

        def outputParameters = getJobOutputParameters(result.jobId, 1)

        def jobProperties = getJobProperties(result.jobId)

        then: "Verify results"
        verifyAll {
            result.outcome == 'success'

            assert jobProperties

            jobSummary.contains(expectedSummary)

            result.logs.contains(expectedLog)

            for (int i=0;i<numberPoolsOfList; i++){
                assert outputParameters =~ /$namePool+$i/
            }

            def poolsInfo = helper.getPools()
            outputParameters.poolGetList =~ poolsInfo.items
        }

        where:
        caseId     | configName  | resultPropertySheet | summary                               | expectedSummary           | expectedLog
        TC.C387258 | CONFIG_NAME | '/myJob/poolGet'    | "Balancing pool list has been gotten" | expectedSummaries.getList | expectedLogs.getList
    }

    @NewFeature(pluginVersion = "3.0.0")
    @Unroll
    def 'Get pool list: Positive #caseId.id #caseId.description'(){

        given: "Tests parameters for procedure LTM - Get pool list"

        def procedureParams = [
                config             : configName,
                resultPropertySheet:  resultPropertySheet,
        ]

        when: "LTM - Get pool list"
        def result = runProcedure(projectName, procedureName, procedureParams)
        def jobSummary = getJobProperty("/myJob/jobSteps/$procedureName/summary", result.jobId)

        def outputParameters = getJobOutputParameters(result.jobId, 1)

        def jobProperties = getJobProperties(result.jobId)

        then: "Verify results"
        verifyAll {
            result.outcome == 'success'

            assert jobProperties

            jobSummary.contains(expectedSummary)

            result.logs.contains(expectedLog)

            for (int i=0;i<numberPoolsOfList; i++){
                assert outputParameters =~ /$namePool+$i/
            }

            def poolsInfo = helper.getPools()
            outputParameters.poolGetList =~ poolsInfo.items
        }

        where:
        caseId     | configName  | resultPropertySheet | summary                               | expectedSummary           | expectedLog
        TC.C387258 | CONFIG_NAME | '/myJob/poolGet'    | "Balancing pool list has been gotten" | expectedSummaries.getList | expectedLogs.getList
    }

    @NewFeature(pluginVersion = "3.0.0")
    @Unroll
    def 'Get pool list: Negative #caseId.id #caseId.description'(){

        given: "Tests parameters for procedure LTM - Get pool list"

        def procedureParams = [
                config             : configName,
                resultPropertySheet:  resultPropertySheet,
        ]

        when: "LTM - Get pool list"
        def result = runProcedure(projectName, procedureName, procedureParams)
        def jobSummary = getJobProperty("/myJob/jobSteps/$procedureName/summary", result.jobId)
       
        def outputParameters = getJobOutputParameters(result.jobId, 1)

        def jobProperties = getJobProperties(result.jobId)

        then: "Verify results"
        verifyAll {
            result.outcome == 'error'

            assert jobProperties

            jobSummary.contains(expectedSummary)

            result.logs.contains(expectedLog)

            assert !outputParameters
        }

        where:
        caseId     | configName  | resultPropertySheet | expectedSummary                 | expectedLog
        TC.C387259 | ''          | '/myJob/poolGet'    | expectedSummaries.emptyConfig   | expectedSummaries.emptyConfig
        TC.C387260 | CONFIG_NAME | ''                  | expectedSummaries.emptyResult   | expectedSummaries.emptyResult
        TC.C387261 | 'notExist'  | '/myJob/poolGet'    | expectedSummaries.nonexisConfig | expectedSummaries.nonexisConfig
        TC.C387262 | CONFIG_NAME | '/sheetNonExist'    | expectedSummaries.wrongResult   | expectedSummaries.wrongResult
    }
}
