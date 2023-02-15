package com.electriccloud.plugin.spec

import com.electriccloud.plugins.annotations.*
import spock.lang.*

//@Ignore
class LTMGetBalancingPoolTests extends PluginTestHelper{

    @Shared
    def procedureName = "LTM - Get balancing pool",
        projectName = "TestProject: Get balancing pool",
        helper,

        TC = [
                C387240:  [id: 'C387240', description: 'Run with required fields'],
                //Negative TC
                C387247:  [id: 'C387247', description: 'Run with an empty configName'],
                C387249:  [id: 'C387249', description: 'Run with an empty partition'],
                C387250:  [id: 'C387250', description: 'Run with an empty namePool'],
                C387251:  [id: 'C387251', description: 'Run with an empty resultPropertySheet'],
                C387248:  [id: 'C387248', description: 'Run with non-exist configName'],
                C387252:  [id: 'C387252', description: 'Run with invalid partition'],
                C387253:  [id: 'C387253', description: 'Run with non-exist namePool'],
                C387254:  [id: 'C387254', description: 'Run with /sheetNonExist resultPropertySheet']
        ],
        expectedSummaries = [
                getPool           : 'Balancing pool PART~POOL has been gotten',
                emptyConfig       : 'No config name',
                emptyPartition    : 'Required parameter "partition" is missing',
                emptyPoolName     : 'Required parameter "name" is missing',
                emptyResult       : 'Required parameter "resultPropertySheet" is missing',
                nonexisConfig     : 'Configuration "configNonExist" does not exist',
                nonexistPartition : 'The requested Pool (/PART/POOL) was not found.',
                nonexisPool       : 'The requested Pool (/PART/POOL) was not found.',
                wrongResult       : 'Unrecognized path element in \'/sheetNonExist\': \'sheetNonExist\''
        ],
        expectedLogs = [
                getPool : "GET ${bigIpProtocol}://${bigIpHost}:${bigIpPort}/mgmt/tm/ltm/pool/~PART~POOL"
        ],
        outputErrors = [
               poolNotFound  : '{"code":404,"message":"01020036:3: The requested Pool (/PART/POOL) was not found.","errorStack":[],"apiError":3}',
        ]

    def doSetupSpec() {
        helper = getBigIpHelper()
        createConfiguration(CONFIG_NAME)
        dslFile "dsl/RunProcedure.dsl", [projectName: projectName, resName: 'local', procedureName: procedureName, params: getBalancingPoolParams]
        helper.createBalancingPool('Common','testPoolName')
    }

    def doCleanupSpec() {
        conditionallyDeleteProject(projectName)
        deleteConfiguration(PLUGIN_NAME, CONFIG_NAME)

        try {
            helper.deleteBalancingPool('Common', 'testPoolName')
        } catch (Throwable e) {
            logger.debug(e.message)
        }
    }

    @Sanity
    @Unroll
    def 'Get balancing pool: Sanity Positive #caseId.id #caseId.description'(){

        given: "Tests parameters for procedure LTM - Get balancing pool"

        def procedureParams = [
                config             : configName,
                partition          : partition,
                name               : namePool,
                resultPropertySheet:  resultPropertySheet,
        ]

        when: "LTM - Get balancing pool"
        def result = runProcedure(projectName, procedureName, procedureParams)
        def jobSummary = getJobProperty("/myJob/jobSteps/$procedureName/summary", result.jobId)

        def outputParameters = getJobOutputParameters(result.jobId, 1)

        def jobProperties = getJobProperties(result.jobId)

        then: "Verify results"
        verifyAll {
            result.outcome == 'success'

            assert jobProperties

            jobSummary.contains(expectedSummary.replace('PART',partition).replace('POOL',namePool))

            result.logs.contains(expectedLog.replace('PART',partition).replace('POOL',namePool))

            def poolInfo = helper.getBalancingPoolInfo(namePool)
            outputParameters.poolGet =~ poolInfo
        }

        where:
        caseId     | configName  | partition | namePool       | resultPropertySheet | expectedSummary           | expectedLog
        TC.C387240 | CONFIG_NAME | 'Common'  | 'testPoolName' | '/myJob/poolGet'    | expectedSummaries.getPool | expectedLogs.getPool
    }

    @NewFeature(pluginVersion = "3.0.0")
    @Unroll
    def 'Get balancing pool: Positive #caseId.id #caseId.description'(){

        given: "Tests parameters for procedure LTM - Get balancing pool"

        def procedureParams = [
                config             : configName,
                partition          : partition,
                name               : namePool,
                resultPropertySheet:  resultPropertySheet,
        ]

        when: "LTM - Get balancing pool"
        def result = runProcedure(projectName, procedureName, procedureParams)
        def jobSummary = getJobProperty("/myJob/jobSteps/$procedureName/summary", result.jobId)

        def outputParameters = getJobOutputParameters(result.jobId, 1)

        def jobProperties = getJobProperties(result.jobId)

        then: "Verify results"
        verifyAll {
            result.outcome == 'success'

            assert jobProperties

            jobSummary.contains(expectedSummary.replace('PART',partition).replace('POOL',namePool))

            result.logs.contains(expectedLog.replace('PART',partition).replace('POOL',namePool))

            def poolInfo = helper.getBalancingPoolInfo(namePool)
            outputParameters.poolGet =~ poolInfo
        }

        where:
        caseId     | configName  | partition | namePool       | resultPropertySheet | expectedSummary           | expectedLog
        TC.C387240 | CONFIG_NAME | 'Common'  | 'testPoolName' | '/myJob/poolGet'    | expectedSummaries.getPool | expectedLogs.getPool
    }

    @NewFeature(pluginVersion = "3.0.0")
    @Unroll
    def 'Get balancing pool: Negative #caseId.id #caseId.description'(){

        given: "Tests parameters for procedure LTM - Get balancing pool"

        def procedureParams = [
                config             : configName,
                partition          : partition,
                name               : namePool,
                resultPropertySheet:  resultPropertySheet,
        ]

        when: "LTM - Get balancing pool"
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

            jobSummary.contains(expectedSummary.replace('PART',partition).replace('POOL',namePool))

            assert jobProperties

            if (error){
//                outputParameters.poolGet == error.replace('PART',partition).replace('POOL',namePool)
                assert outputParameters.poolGet == null

//                propertyName == error.replace('PART',partition).replace('POOL',namePool)
                assert propertyName == null
            }
            else {
                assert !outputParameters
                assert !propertyName
            }
        }

        where:
        caseId     | configName      | partition | namePool       | resultPropertySheet | expectedSummary                     | error
        TC.C387247 | ''              | 'Common'  | 'testPoolName' | '/myJob/poolGet'    | expectedSummaries.emptyConfig       | ''
        TC.C387249 | CONFIG_NAME     | ''        | 'testPoolName' | '/myJob/poolGet'    | expectedSummaries.emptyPartition    | ''
        TC.C387250 | CONFIG_NAME     | 'Common'  | ''             | '/myJob/poolGet'    | expectedSummaries.emptyPoolName     | ''
        TC.C387251 | CONFIG_NAME     | 'Common'  | 'testPoolName' | ''                  | expectedSummaries.emptyResult       | ''
        TC.C387248 | 'configNonExist'| 'Common'  | 'testPoolName' | '/myJob/poolGet'    | expectedSummaries.nonexisConfig     | ''
        TC.C387252 | CONFIG_NAME     | 'invalid' | 'testPoolName' | '/myJob/poolGet'    | expectedSummaries.nonexistPartition | outputErrors.poolNotFound
        TC.C387253 | CONFIG_NAME     | 'Common'  | 'notExist'     | '/myJob/poolGet'    | expectedSummaries.nonexisPool       | outputErrors.poolNotFound
        TC.C387254 | CONFIG_NAME     | 'Common'  | 'testPoolName' | '/sheetNonExist'    | expectedSummaries.wrongResult       | ''
    }
}
