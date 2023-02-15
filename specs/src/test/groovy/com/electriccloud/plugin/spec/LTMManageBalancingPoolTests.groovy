package com.electriccloud.plugin.spec

import com.electriccloud.plugins.annotations.*
import groovy.json.JsonSlurper
import spock.lang.*

@Ignore
// test suite is deprecated
// http://jira.electric-cloud.com/browse/ECPBIGIP-48
class LTMManageBalancingPoolTests extends PluginTestHelper{

    @Shared
    def projectName = 'TestProject: Manage balancing pool',
        procedureName = 'LTM - Manage balancing pool',
        bigIpClient,
        testCaseHelper,

        TC = [
                C387435: [id: 'C387435', description: 'Update(default) pool with optionalParameters'],
                C387437: [id: 'C387437', description: 'Remove and Create existing pool with optionalParameters'],
                C387441: [id: 'C387441', description: 'Remove and Create existing pool without new data'],
                //Negative TCs
                C387405: [id: 'C387405', description: 'empty Configuration Name'],
                C387406: [id: 'C387406', description: 'empty Action'],
                C387407: [id: 'C387407', description: 'empty Partition Name'],
                C387408: [id: 'C387408', description: 'empty Pool Name'],
                C387410: [id: 'C387410', description: 'empty Result Property Sheet'],
                C387411: [id: 'C387411', description: 'nonexistent Configuration Name'],
                C387412: [id: 'C387412', description: 'wrong Action'],
                C387413: [id: 'C387413', description: '\'Update\' action for nonexistent balancing pool'],
                C387414: [id: 'C387414', description: 'nonexistent Partition'],
                C387415: [id: 'C387415', description: 'with /invalid/invalid Result Property Sheet'],
                C387438: [id: 'C387438', description: 'Remove and Create for nonexistent pool with only required parameters'],
                C387439: [id: 'C387439', description: 'Remove and Create for nonexistent pool with optionalParameters']
        ],

        expectedSummaries = [
                updated           : 'Balancing pool PART~POOL has been updated',
                replaced          : 'Balancing pool PART~POOL has been replaced',
                emptyConfig       : 'No config name',
                emptyMethod       : 'Required parameter "method" is missing',
                emptyPartition    : 'Required parameter "partition" is missing',
                emptyPoolName     : 'Required parameter "name" is missing',
                emptyResult       : 'Required parameter "resultPropertySheet" is missing',
                nonexisConfig     : 'Configuration "nonExistent" does not exist',
                wrongMethod       : 'Wrong method value: wrong',
                nonexisPool       : 'one or more properties must be specified',
                nonexistPartition : 'The requested folder (/nonExistent) was not found.',
                wrongResult       : 'Unrecognized path element in \'/invalid/invalid\': \'invalid\'',
                nonexisPoolName   : 'The requested Pool (/PART/POOL) was not found.'
        ],

        expectedLogs = [
                updated : "PATCH ${BIGIP_LOCAL_PROTOCOL}://${BIGIP_LOCAL_HOST}:${BIGIP_LOCAL_PORT}/mgmt/tm/ltm/pool/~PART~POOL",
                replaced : "PUT ${BIGIP_LOCAL_PROTOCOL}://${BIGIP_LOCAL_HOST}:${BIGIP_LOCAL_PORT}/mgmt/tm/ltm/pool/~PART~POOL"
        ],

        outputErrors = [
                pool: '{"code":400,"message":"one or more properties must be specified","errorStack":[],"apiError":26214401}',
                partition: '{"code":400,"message":"The requested folder (/nonExistent) was not found.","errorStack":[],"apiError":26214401}',
                poolName: '{"code":404,"message":"01020036:3: The requested Pool (/PART/POOL) was not found.","errorStack":[],"apiError":3}'
        ]

    def doSetupSpec() {
        bigIpClient = getBigIpHelper()
        testCaseHelper = new TestCaseHelper(procedureName)
        createConfiguration(CONFIG_NAME)
        dslFile "dsl/RunProcedure.dsl", [projectName: projectName, resName: 'local', procedureName: procedureName, params: manageBalancingPoolParams]
    }

    def doCleanupSpec() {
        testCaseHelper.createTestCases()
        conditionallyDeleteProject(projectName)
        deleteConfiguration(PLUGIN_NAME, CONFIG_NAME)
    }

    @Sanity
    @Unroll
    def 'ManageBalancingPool: Sanity #caseId.id #caseId.description'() {
        bigIpClient.createBalancingPool(partition, namePool)

        given: "Tests parameters for procedure LTM - Manage balancing pool"
        def procedureParams = [
                config             : configName,
                method             : method,
                name               : namePool,
                optionalParameters : optionalParameters,
                partition          : partition,
                resultPropertySheet: resultPropertySheet,
        ]

        when: "Run procedure"
        def result = runProcedure(projectName, procedureName, procedureParams)
        def jobSummary = getJobProperty("/myJob/jobSteps/$procedureName/summary", result.jobId)

        def outputParameters = getJobOutputParameters(result.jobId, 1)
        def jobProperties = getJobProperties(result.jobId)

        def poolInfo = bigIpClient.getBalancingPoolInfo(namePool)
        poolInfo.selfLink = poolInfo.selfLink.replace(namePool, "~$partition~$namePool")

        def propertyName = jobProperties[resultPropertySheet.split("/")[2]]
        then: "Verify results"
        verifyAll {
            result.outcome == 'success'

            jobSummary.contains(expectedSummary
                    .replace('PART',partition)
                    .replace('POOL', namePool))

            result.logs.contains(expectedLog
                    .replace('PART',partition)
                    .replace('POOL', namePool))

            new JsonSlurper().parseText(propertyName) =~ poolInfo

            new JsonSlurper().parseText(outputParameters.poolManage) =~ poolInfo

            if (optionalParameters) {
                for (param in optionalParameters.split(";")) {
                    poolInfo[param.split("=")[0]] == param.split("=")[1]
                }
            }

            poolInfo
            poolInfo.name == namePool
        }

        cleanup:
        try {
            bigIpClient.deleteBalancingPool(partition, namePool)
        } catch (Throwable e) {
            logger.debug(e.message)
        }

        where:
        caseId     | configName    | method  | namePool        | optionalParameters        | partition    | resultPropertySheet | expectedSummary            | expectedLog
        TC.C387441 | CONFIG_NAME   | 'PUT'   | 'testPoolName'  | ''                        | 'Common'     | '/myJob/poolManage' | expectedSummaries.replaced | expectedLogs.replaced
        TC.C387435 | CONFIG_NAME   | 'PATCH' | 'testPoolName'  | 'allowNat=no;allowSnat=no'| 'Common'     | '/myJob/poolManage' | expectedSummaries.updated  | expectedLogs.updated
    }

    @NewFeature(pluginVersion = "3.0.0")
    @Unroll
    def 'ManageBalancingPool: Positive #caseId.id #caseId.description'() {
        testCaseHelper.createNewTestCase(caseId.id, caseId.description)

        testCaseHelper.testCasePrecondition("Create Balancing Pool with name $namePool")
        bigIpClient.createBalancingPool(partition, namePool)

        given: "Tests parameters for procedure LTM - Manage balancing pool"
        def procedureParams = [
                config             : configName,
                method             : method,
                name               : namePool,
                optionalParameters : optionalParameters,
                partition          : partition,
                resultPropertySheet: resultPropertySheet,
        ]
        testCaseHelper.addStepContent("Run procedure $procedureName with parameters:", procedureParams)

        when: "Run procedure"
        def result = runProcedure(projectName, procedureName, procedureParams)
        def jobSummary = getJobProperty("/myJob/jobSteps/$procedureName/summary", result.jobId)

        def outputParameters = getJobOutputParameters(result.jobId, 1)
        def jobProperties = getJobProperties(result.jobId)

        def poolInfo = bigIpClient.getBalancingPoolInfo(namePool)
        poolInfo.selfLink = poolInfo.selfLink.replace(namePool, "~$partition~$namePool")

        def propertyName = jobProperties[resultPropertySheet.split("/")[2]]
        then: "Verify results"
        verifyAll {
            testCaseHelper.addExpectedResult("Job status: success")
            result.outcome == 'success'

            testCaseHelper.addExpectedResult("Job Summary: ${expectedSummary.replace('PART',partition).replace('POOL', namePool)}")
            jobSummary.contains(expectedSummary
                    .replace('PART',partition)
                    .replace('POOL', namePool))

            testCaseHelper.addExpectedResult("Job Logs: $expectedLog")
            result.logs.contains(expectedLog
                    .replace('PART',partition)
                    .replace('POOL', namePool))

            testCaseHelper.addExpectedResult("Job Properties:")
            testCaseHelper.addExpectedResult("1. $resultPropertySheet: $propertyName")
            new JsonSlurper().parseText(propertyName) =~ poolInfo

            testCaseHelper.addExpectedResult("Job OutputParameters:")
            testCaseHelper.addExpectedResult("1. poolManage: ${outputParameters.poolManage}")
            new JsonSlurper().parseText(outputParameters.poolManage) =~ poolInfo

            testCaseHelper.addExpectedResult("BigIp: balancing pool $namePool should be updated")

            if (optionalParameters) {
                for (param in optionalParameters.split(";")) {
                    testCaseHelper.addExpectedResult("BigIp: balancing pool $namePool should have option ${param.split("=")[0]} with value ${param.split("=")[1]}")
                    poolInfo[param.split("=")[0]] == param.split("=")[1]
                }
            }

            testCaseHelper.addExpectedResult("balancing pool json: ${poolInfo}")
            poolInfo
            poolInfo.name == namePool
        }

        cleanup:
        try {
            bigIpClient.deleteBalancingPool(partition, namePool)
        } catch (Throwable e) {
            logger.debug(e.message)
        }

        where:
        caseId     | configName    | method  | namePool        | optionalParameters        | partition    | resultPropertySheet | expectedSummary            | expectedLog
        TC.C387435 | CONFIG_NAME   | 'PATCH' | 'testPoolName'  | 'allowNat=no;allowSnat=no'| 'Common'     | '/myJob/poolManage' | expectedSummaries.updated  | expectedLogs.updated
        TC.C387437 | CONFIG_NAME   | 'PUT'   | 'testPoolName'  | 'allowNat=no;allowSnat=no'| 'Common'     | '/myJob/poolManage' | expectedSummaries.replaced | expectedLogs.replaced
        TC.C387441 | CONFIG_NAME   | 'PUT'   | 'testPoolName'  | ''                        | 'Common'     | '/myJob/poolManage' | expectedSummaries.replaced | expectedLogs.replaced
    }

    @NewFeature(pluginVersion = "3.0.0")
    @Unroll
    def 'ManageBalancingPool: Negative #caseId.id #caseId.description'() {
        testCaseHelper.createNewTestCase(caseId.id, caseId.description)

        given: "Tests parameters for procedure LTM - Manage balancing pool"
        def procedureParams = [
                config             : configName,
                method             : method,
                name               : namePool,
                optionalParameters : optionalParameters,
                partition          : partition,
                resultPropertySheet: resultPropertySheet,
        ]
        testCaseHelper.addStepContent("Run procedure $procedureName with parameters:", procedureParams)

        when: "Run procedure"
        def result = runProcedure(projectName, procedureName, procedureParams)
        def jobSummary = getJobProperty("/myJob/jobSteps/$procedureName/summary", result.jobId)

        def outputParameters = getJobOutputParameters(result.jobId, 1)
        def jobProperties = getJobProperties(result.jobId)

        def propertyName
        if (resultPropertySheet && (resultPropertySheet != '/invalid')) {
            propertyName = jobProperties[resultPropertySheet.split("/")[2]]
        }

        then: "Verify results"
        verifyAll {
            testCaseHelper.addExpectedResult("Job status: error")
            result.outcome == 'error'

            testCaseHelper.addExpectedResult("Job Summary: $expectedSummary")
            jobSummary.contains(expectedSummary
                    .replace('PART', partition)
                    .replace('POOL', namePool))

            testCaseHelper.addExpectedResult("Job Logs: $expectedLog")
            result.logs.contains(expectedLog
                    .replace('PART', partition)
                    .replace('POOL', namePool))

            if (error){
                testCaseHelper.addExpectedResult("Output parameter should contains error message: $error")
                outputParameters.poolManage == error
                        .replace('PART', partition)
                        .replace('POOL', namePool)
                testCaseHelper.addExpectedResult("property $propertyName should contains error message: $error")
                propertyName == error
                        .replace('PART', partition)
                        .replace('POOL', namePool)
            }
            else {
                testCaseHelper.addExpectedResult("Procedure shouldn't have outputParameters")
                assert !outputParameters
                if (resultPropertySheet) {
                    testCaseHelper.addExpectedResult("Procedure shouldn't have property: ${resultPropertySheet.split("/")[2]}")
                }
                assert !propertyName
            }
        }

        where:
        caseId     | configName    | method  | namePool          | optionalParameters        | partition     | resultPropertySheet | expectedSummary                     | expectedLog                          | error
        TC.C387405 | ''            | 'PATCH' | 'testPoolName'    | ''                        | 'Common'      | '/myJob/poolManage' | expectedSummaries.emptyConfig       | expectedSummaries.emptyConfig        | ''
        TC.C387406 | CONFIG_NAME   | ''      | 'testPoolName'    | ''                        | 'Common'      | '/myJob/poolManage' | expectedSummaries.emptyMethod       | expectedSummaries.emptyMethod        | ''
        TC.C387407 | CONFIG_NAME   | 'PATCH' | 'testPoolName'    | ''                        | ''            | '/myJob/poolManage' | expectedSummaries.emptyPartition    | expectedSummaries.emptyPartition     | ''
        TC.C387408 | CONFIG_NAME   | 'PATCH' | ''                | ''                        | 'Common'      | '/myJob/poolManage' | expectedSummaries.emptyPoolName     | expectedSummaries.emptyPoolName      | ''
        TC.C387410 | CONFIG_NAME   | 'PATCH' | 'testPoolName'    | ''                        | 'Common'      | ''                  | expectedSummaries.emptyResult       | expectedSummaries.emptyResult        | ''
        TC.C387411 | 'nonExistent' | 'PATCH' | 'testPoolName'    | ''                        | 'Common'      | '/myJob/poolManage' | expectedSummaries.nonexisConfig     | expectedSummaries.nonexisConfig      | ''
        TC.C387412 | CONFIG_NAME   | 'wrong' | 'testPoolName'    | ''                        | 'Common'      | '/myJob/poolManage' | expectedSummaries.wrongMethod       | expectedSummaries.wrongMethod        | ''
        TC.C387413 | CONFIG_NAME   | 'PATCH' | 'nonExistent'     | ''                        | 'Common'      | '/myJob/poolManage' | expectedSummaries.nonexisPool       | expectedLogs.updated                 | outputErrors.pool
        TC.C387414 | CONFIG_NAME   | 'PATCH' | 'testPoolName'    | ''                        | 'nonExistent' | '/myJob/poolManage' | expectedSummaries.nonexistPartition | expectedLogs.updated                 | outputErrors.partition
        TC.C387415 | CONFIG_NAME   | 'PATCH' | 'testPoolName'    | ''                        | 'Common'      | '/invalid/invalid'  | expectedSummaries.wrongResult       | expectedLogs.updated                 | ''
        TC.C387438 | CONFIG_NAME   | 'PUT'   | 'NewPoolName'     | ''                        | 'Common'      | '/myJob/poolManage' | expectedSummaries.nonexisPoolName   | expectedLogs.replaced                | outputErrors.poolName
        TC.C387439 | CONFIG_NAME   | 'PUT'   | 'NewPoolName'     | 'allowNat=no;allowSnat=no'| 'Common'      | '/myJob/poolManage' | expectedSummaries.nonexisPoolName   | expectedLogs.replaced                | outputErrors.poolName
    }
}
