package com.electriccloud.plugin.spec

import com.electriccloud.plugins.annotations.*
import spock.lang.*

//@Ignore
class LTMDeleteBalancingPoolTests extends PluginTestHelper {

    @Shared
    def projectName = 'TestProject: Delete balancing pool',
        procedureName = 'LTM - Delete balancing pool',
        bigIpClient,
        testCaseHelper,

        TC = [
                C387516: [id: 'C387516', description: 'delete pool without members'],
                C387517: [id: 'C387517', description: 'delete pool with members'],
                //Negative TCs
                C387518: [id: 'C387518', description: 'empty Configuration Name'],
                C387519: [id: 'C387519', description: 'empty Partition Name'],
                C387520: [id: 'C387520', description: 'empty Pool Name'],
                C387521: [id: 'C387521', description: 'empty Result Property Sheet'],
                C387522: [id: 'C387522', description: 'nonexistent Configuration Name'],
                C387523: [id: 'C387523', description: 'nonexistent Pool'],
                C387524: [id: 'C387524', description: 'nonexistent Partition'],
                C387525: [id: 'C387525', description: 'with /invalid/invalid Result Property Sheet']
        ],

        expectedSummaries = [
                deleted           : 'Balancing pool PART~POOL has been deleted',
                emptyConfig       : 'No config name',
                emptyPartition    : 'Required parameter "partition" is missing',
                emptyPoolName     : 'Required parameter "name" is missing',
                emptyResult       : 'Required parameter "resultPropertySheet" is missing',
                nonexisConfig     : 'Configuration "nonExistent" does not exist',
                nonexisPoolName   : 'The requested Pool (/PART/POOL) was not found.',
                nonexistPartition : 'The requested Pool (/PART/POOL) was not found.',
                wrongResult       : 'Unrecognized path element in \'/invalid/invalid\': \'invalid\''
        ],

        expectedLogs = [
                deleted : "DELETE ${bigIpProtocol}://${bigIpHost}:${bigIpPort}/mgmt/tm/ltm/pool/~PART~POOL"
        ],

        outputErrors = [
                poolName: '{"code":404,"message":"01020036:3: The requested Pool (/PART/POOL) was not found.","errorStack":[],"apiError":3}'
        ]

    def doSetupSpec() {
        bigIpClient = getBigIpHelper()
        testCaseHelper = new TestCaseHelper(procedureName)
        createConfiguration(CONFIG_NAME)
        dslFile "dsl/RunProcedure.dsl", [projectName: projectName, resName: 'local', procedureName: procedureName, params: deleteBalancingPoolParams]
    }

    def doCleanupSpec() {
        testCaseHelper.createTestCases()
        conditionallyDeleteProject(projectName)
        deleteConfiguration(PLUGIN_NAME, CONFIG_NAME)
    }

    @Sanity
    @Unroll
    def 'DeleteBalancingPool: Sanity Positive #caseId.id #caseId.description'() {

        bigIpClient.createBalancingPool(partition, namePool)
        def preconditionPoolsInfo = bigIpClient.getPools()

        given: "Tests parameters for procedure LTM - Delete balancing pool"
        def procedureParams = [
                config             : configName,
                name               : namePool,
                partition          : partition,
                resultPropertySheet: resultPropertySheet,
        ]

        when: "Run procedure"
        def result = runProcedure(projectName, procedureName, procedureParams)
        def jobSummary = getJobProperty("/myJob/jobSteps/$procedureName/summary", result.jobId)

        def poolsInfo = bigIpClient.getPools()

        then: "Verify results"
        verifyAll {
            result.outcome == 'success'

            jobSummary.contains(expectedSummary
                    .replace('PART',partition)
                    .replace('POOL', namePool))

            result.logs.contains(expectedLog
                    .replace('PART',partition)
                    .replace('POOL', namePool))

            preconditionPoolsInfo.items != poolsInfo.items

            for (pool in poolsInfo.items){
                pool.name != namePool
            }
        }

        where:
        caseId     | configName  | namePool              | partition | resultPropertySheet | expectedSummary           | expectedLog
        TC.C387516 | CONFIG_NAME | 'testPoolName'        | 'Common'  | '/myJob/poolDelete' | expectedSummaries.deleted | expectedLogs.deleted
    }

    @NewFeature(pluginVersion = "3.0.0")
    @Unroll
    def 'DeleteBalancingPool: Positive #caseId.id #caseId.description'() {
        testCaseHelper.createNewTestCase(caseId.id, caseId.description)

        testCaseHelper.testCasePrecondition("Create Balancing Pool with name $namePool")
        bigIpClient.createBalancingPool(partition, namePool)
        def preconditionPoolsInfo = bigIpClient.getPools()

        if (caseId.id == 'C387517' ) {
            testCaseHelper.testCasePrecondition("Create Pool Members with names \'1.1.1.1-3:80\'")
            for (int i=1;i<=3;i++){
                bigIpClient.createPoolMember(partition, namePool, "1.1.1.$i:80")
            }
        }

        given: "Tests parameters for procedure LTM - Delete balancing pool"
        def procedureParams = [
                config             : configName,
                name               : namePool,
                partition          : partition,
                resultPropertySheet: resultPropertySheet,
        ]
        testCaseHelper.addStepContent("Run procedure $procedureName with parameters:", procedureParams)

        when: "Run procedure"

        def result = runProcedure(projectName, procedureName, procedureParams)
        def jobSummary = getJobProperty("/myJob/jobSteps/$procedureName/summary", result.jobId)

        def outputParameters = getJobOutputParameters(result.jobId, 1)
        def jobProperties = getJobProperties(result.jobId)

        def poolsInfo = bigIpClient.getPools()

        def propertyName = jobProperties[resultPropertySheet.split("/")[2]]

        then: "Verify results"
        verifyAll {
            testCaseHelper.addExpectedResult("Job status: success")
            result.outcome == 'success'

            testCaseHelper.addExpectedResult("Job Summary: ${expectedSummary.replace('PART',partition).replace('POOL', namePool)}")
            jobSummary.contains(expectedSummary
                    .replace('PART',partition)
                    .replace('POOL', namePool))

            testCaseHelper.addExpectedResult("Job Logs: ${expectedLog.replace('PART',partition).replace('POOL', namePool)}")
            result.logs.contains(expectedLog
                    .replace('PART',partition)
                    .replace('POOL', namePool))

            testCaseHelper.addExpectedResult("Job Properties:")
            testCaseHelper.addExpectedResult("1. $resultPropertySheet: $propertyName")

            testCaseHelper.addExpectedResult("Job OutputParameters:")
            testCaseHelper.addExpectedResult("1. poolDelete: ${outputParameters.poolDelete}")

            testCaseHelper.addExpectedResult("BigIp: balancing pool $namePool should be deleted")
            preconditionPoolsInfo.items != poolsInfo.items
            for (pool in poolsInfo.items){
                pool.name != namePool
            }
        }

        cleanup:
        if (caseId.id == 'C387517' ) {
            for (int i=1;i<=3;i++){
                bigIpClient.deleteNode(partition, "1.1.1.$i:80")
            }
        }

        where:
        caseId     | configName  | namePool              | partition | resultPropertySheet | expectedSummary           | expectedLog
        TC.C387516 | CONFIG_NAME | 'testPoolName'        | 'Common'  | '/myJob/poolDelete' | expectedSummaries.deleted | expectedLogs.deleted
        TC.C387517 | CONFIG_NAME | 'testPoolWithMembers' | 'Common'  | '/myJob/poolDelete' | expectedSummaries.deleted | expectedLogs.deleted
    }

    @NewFeature(pluginVersion = "3.0.0")
    @Unroll
    def 'DeleteBalancingPool: Negative #caseId.id #caseId.description'() {
        testCaseHelper.createNewTestCase(caseId.id, caseId.description)

        given: "Tests parameters for procedure LTM - Delete balancing pool"
        def procedureParams = [
                config             : configName,
                name               : namePool,
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
        if (resultPropertySheet && (resultPropertySheet != '/invalid/invalid')) {
            propertyName = jobProperties[resultPropertySheet.split("/")[2]]
        }

        then: "Verify results"
        verifyAll {
            testCaseHelper.addExpectedResult("Job status: error")
            result.outcome == 'error'

            testCaseHelper.addExpectedResult("Job Summary: ${expectedSummary.replace('PART',partition).replace('POOL', namePool)}")
            jobSummary.contains(expectedSummary
                    .replace('PART', partition)
                    .replace('POOL', namePool))

            testCaseHelper.addExpectedResult("Job Logs: ${expectedLog.replace('PART',partition).replace('POOL', namePool)}")
            result.logs.contains(expectedLog
                    .replace('PART', partition)
                    .replace('POOL', namePool))

            if (error){
//                testCaseHelper.addExpectedResult("Output parameter should contains error message: ${error.replace('PART',partition).replace('POOL', namePool)}")
//                outputParameters.poolDelete == error
//                        .replace('PART', partition)
//                        .replace('POOL', namePool)
                assert outputParameters.poolDelete == null

//                testCaseHelper.addExpectedResult("property $propertyName should contains error message: ${error.replace('PART',partition).replace('POOL', namePool)}")
//                propertyName == error
//                        .replace('PART', partition)
//                        .replace('POOL', namePool)
                assert propertyName == null
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
        caseId     | configName    | namePool       | partition     | resultPropertySheet | expectedSummary                     | expectedLog                      | error
        TC.C387518 | ''            | 'testPoolName' | 'Common'      | '/myJob/poolDelete' | expectedSummaries.emptyConfig       | expectedSummaries.emptyConfig    | ''
        TC.C387520 | CONFIG_NAME   | ''             | 'Common'      | '/myJob/poolDelete' | expectedSummaries.emptyPoolName     | expectedSummaries.emptyPoolName  | ''
        TC.C387519 | CONFIG_NAME   | 'testPoolName' | ''            | '/myJob/poolDelete' | expectedSummaries.emptyPartition    | expectedSummaries.emptyPartition | ''
        TC.C387521 | CONFIG_NAME   | 'testPoolName' | 'Common'      | ''                  | expectedSummaries.emptyResult       | expectedSummaries.emptyResult    | ''
        TC.C387522 | 'nonExistent' | 'testPoolName' | 'Common'      | '/myJob/poolDelete' | expectedSummaries.nonexisConfig     | expectedSummaries.nonexisConfig  | ''
        TC.C387523 | CONFIG_NAME   | 'nonExistent'  | 'Common'      | '/myJob/poolDelete' | expectedSummaries.nonexisPoolName   | expectedLogs.deleted             | outputErrors.poolName
        TC.C387524 | CONFIG_NAME   | 'testPoolName' | 'nonExistent' | '/myJob/poolDelete' | expectedSummaries.nonexistPartition | expectedLogs.deleted             | outputErrors.poolName
//        TC.C387525 | CONFIG_NAME   | 'testPoolName' | 'Common'      | '/invalid/invalid'  | expectedSummaries.wrongResult       | expectedLogs.deleted             | ''
        TC.C387525 | CONFIG_NAME   | 'testPoolName' | 'Common'      | '/invalid/invalid'  | expectedSummaries.nonexisPoolName   | expectedLogs.deleted             | ''
    }
}