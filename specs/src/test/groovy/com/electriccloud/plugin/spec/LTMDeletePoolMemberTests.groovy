package com.electriccloud.plugin.spec

import com.electriccloud.plugins.annotations.*
import spock.lang.*

//@Ignore
class LTMDeletePoolMemberTests extends PluginTestHelper {

    @Shared
    def projectName = 'TestProject: Delete pool member',
        procedureName = 'LTM - Delete pool member',
        bigIpClient,
        testCaseHelper,

        TC = [
                C387584: [id: 'C387584', description: 'delete pool member'],
                //Negative TCs
                C387585: [id: 'C387585', description: 'empty Configuration Name'],
                C387586: [id: 'C387586', description: 'empty Partition Name'],
                C387587: [id: 'C387587', description: 'empty Pool Name'],
                C387588: [id: 'C387588', description: 'empty Member Name'],
                C387589: [id: 'C387589', description: 'empty Result Property Sheet'],
                C387590: [id: 'C387590', description: 'nonexistent Configuration Name'],
                C387591: [id: 'C387591', description: 'nonexistent Pool'],
                C387592: [id: 'C387592', description: 'nonexistent Partition'],
                C387593: [id: 'C387593', description: 'nonexistent Member'],
                C387594: [id: 'C387594', description: 'with /invalid/invalid Result Property Sheet']
        ],

        expectedSummaries = [
                deleted           : 'Balancing pool PART~MEMBER has been deleted',
                emptyConfig       : 'No config name',
                emptyPartition    : 'Required parameter "partition" is missing',
                emptyPoolName     : 'Required parameter "pool_name" is missing',
                emptyMemberName   : 'Required parameter "name" is missing',
                emptyResult       : 'Required parameter "resultPropertySheet" is missing',
                nonexisConfig     : 'Configuration "nonExistent" does not exist',
                nonexisPoolName   : 'The requested Pool Member (/PART/POOL /PART/1.1.1.1 80) was not found.',
                nonexistPartition : 'The requested Pool Member (/PART/POOL /PART/1.1.1.1 80) was not found.',
                nonexistentMember : 'HTML Tag-like Content in the Request URL/Body',
                wrongResult       : 'Unrecognized path element in \'/invalid/invalid\': \'invalid\''
        ],

        expectedLogs = [
                deleted : "DELETE ${bigIpProtocol}://${bigIpHost}:${bigIpPort}/mgmt/tm/ltm/pool/~PART~POOL/members/~PART~MEMBER"
        ],

        outputErrors = [
                memberNotFound: '{"code":404,"message":"01020036:3: The requested Pool Member (/PART/POOL /PART/1.1.1.1 80) was not found.","errorStack":[],"apiError":3}',
                poolMember : '{"code":400,"message":"HTML Tag-like Content in the Request URL/Body","errorStack":[],"apiError":26214401}',
                poolName: '{"code":404,"message":"01020036:3: The requested Pool (/PART/POOL) was not found.","errorStack":[],"apiError":3}'
        ]

    def doSetupSpec() {
        bigIpClient = getBigIpHelper()
        testCaseHelper = new TestCaseHelper(procedureName)
        createConfiguration(CONFIG_NAME)
        dslFile "dsl/RunProcedure.dsl", [projectName: projectName, resName: 'local', procedureName: procedureName, params: deletePoolMemberParams]
    }

    def doCleanupSpec() {
        testCaseHelper.createTestCases()
        conditionallyDeleteProject(projectName)
        deleteConfiguration(PLUGIN_NAME, CONFIG_NAME)
    }

    @Sanity
    @Unroll
    def 'DeletePoolMember: Sanity positive #caseId.id #caseId.description'() {
        bigIpClient.createBalancingPool(partition, namePool)
        bigIpClient.createPoolMember(partition, namePool, nameMember)

        def preconditionMembersInfo = bigIpClient.getPoolMemberInfo(namePool)

        given: "Tests parameters for procedure LTM - Delete pool member"
        def procedureParams = [
                config             : configName,
                name               : nameMember,
                partition          : partition,
                pool_name          : namePool,
                resultPropertySheet: resultPropertySheet,
        ]

        when: "Run procedure"

        def result = runProcedure(projectName, procedureName, procedureParams)
        def jobSummary = getJobProperty("/myJob/jobSteps/$procedureName/summary", result.jobId)

        def jobProperties = getJobProperties(result.jobId)

        def membersInfo = bigIpClient.getPoolMemberInfo(namePool)

        then: "Verify results"
        verifyAll {
            result.outcome == 'success'

            jobSummary.contains(expectedSummary
                    .replace('PART',partition)
                    .replace('MEMBER', nameMember))

            result.logs.contains(expectedLog
                    .replace('PART',partition)
                    .replace('POOL', namePool)
                    .replace('MEMBER', nameMember))

            preconditionMembersInfo.items != membersInfo.items
            membersInfo.items == []
            for (pool in membersInfo.items){
                pool.name != nameMember
            }
        }

        cleanup:
        try {
            bigIpClient.deleteBalancingPool(partition, namePool)
            bigIpClient.deleteNode(partition, nameMember)
        } catch (Throwable e) {
            logger.debug(e.message)
        }

        where:
        caseId     | configName  | nameMember   | partition | namePool       | resultPropertySheet       | expectedSummary           | expectedLog
        TC.C387584 | CONFIG_NAME | "1.1.1.1:80" | 'Common'  | 'testPoolName' | '/myJob/poolMemberDelete' | expectedSummaries.deleted | expectedLogs.deleted
    }

    @NewFeature(pluginVersion = "3.0.0")
    @Unroll
    def 'DeletePoolMember: Positive #caseId.id #caseId.description'() {
        testCaseHelper.createNewTestCase(caseId.id, caseId.description)

        testCaseHelper.testCasePrecondition("Create Balancing Pool with name $namePool")
        bigIpClient.createBalancingPool(partition, namePool)

        testCaseHelper.testCasePrecondition("Create Pool Member with name $nameMember in pool $namePool")
        bigIpClient.createPoolMember(partition, namePool, nameMember)

        def preconditionMembersInfo = bigIpClient.getPoolMemberInfo(namePool)
        testCaseHelper.testCasePrecondition("Pool Members in pool $namePool: $preconditionMembersInfo")

        given: "Tests parameters for procedure LTM - Delete pool member"
        def procedureParams = [
                config             : configName,
                name               : nameMember,
                partition          : partition,
                pool_name          : namePool,
                resultPropertySheet: resultPropertySheet,
        ]
        testCaseHelper.addStepContent("Run procedure $procedureName with parameters:", procedureParams)

        when: "Run procedure"

        def result = runProcedure(projectName, procedureName, procedureParams)
        def jobSummary = getJobProperty("/myJob/jobSteps/$procedureName/summary", result.jobId)

        def outputParameters = getJobOutputParameters(result.jobId, 1)
        def jobProperties = getJobProperties(result.jobId)

        def propertyName = jobProperties[resultPropertySheet.split("/")[2]]

        def membersInfo = bigIpClient.getPoolMemberInfo(namePool)

        then: "Verify results"
        verifyAll {
            testCaseHelper.addExpectedResult("Job status: success")
            result.outcome == 'success'

            testCaseHelper.addExpectedResult("Job Summary: ${expectedSummary.replace('PART',partition).replace('MEMBER', nameMember)}")
            jobSummary.contains(expectedSummary
                    .replace('PART',partition)
                    .replace('MEMBER', nameMember))

            testCaseHelper.addExpectedResult("Job Logs: ${expectedLog.replace('PART',partition).replace('POOL', namePool).replace('MEMBER', nameMember)}")
            result.logs.contains(expectedLog
                    .replace('PART',partition)
                    .replace('POOL', namePool)
                    .replace('MEMBER', nameMember))

            testCaseHelper.addExpectedResult("Job Properties:")
            testCaseHelper.addExpectedResult("1. $resultPropertySheet: $propertyName")

            testCaseHelper.addExpectedResult("Job OutputParameters:")
            testCaseHelper.addExpectedResult("1. poolMemberDelete: ${outputParameters.poolMemberDelete}")

            testCaseHelper.addExpectedResult("BigIp: pool member $nameMember should be deleted")
            preconditionMembersInfo.items != membersInfo.items
            membersInfo.items == []
            for (pool in membersInfo.items){
                pool.name != nameMember
            }
        }

        cleanup:
        try {
            bigIpClient.deleteBalancingPool(partition, namePool)
            bigIpClient.deleteNode(partition, nameMember)
        } catch (Throwable e) {
            logger.debug(e.message)
        }

        where:
        caseId     | configName  | nameMember   | partition | namePool       | resultPropertySheet       | expectedSummary           | expectedLog
        TC.C387584 | CONFIG_NAME | "1.1.1.1:80" | 'Common'  | 'testPoolName' | '/myJob/poolMemberDelete' | expectedSummaries.deleted | expectedLogs.deleted
    }

    @NewFeature(pluginVersion = "3.0.0")
    @Unroll
    def 'DeletePoolMember: Negative #caseId.id #caseId.description'() {
        testCaseHelper.createNewTestCase(caseId.id, caseId.description)

        given: "Tests parameters for procedure LTM - Delete pool member"
        def procedureParams = [
                config             : configName,
                name               : nameMember,
                partition          : partition,
                pool_name          : namePool,
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

            testCaseHelper.addExpectedResult("Job Summary: ${expectedSummary.replace('PART',partition).replace('POOL', namePool).replace('MEMBER', nameMember)}")
            jobSummary.contains(expectedSummary
                    .replace('PART', partition)
                    .replace('POOL', namePool)
                    .replace('MEMBER', nameMember))

            testCaseHelper.addExpectedResult("Job Logs: ${expectedLog.replace('PART',partition).replace('POOL', namePool).replace('MEMBER', nameMember)}")
            result.logs.contains(expectedLog
                    .replace('PART', partition)
                    .replace('POOL', namePool)
                    .replace('MEMBER', nameMember))

            if (error){
//                testCaseHelper.addExpectedResult("Output parameter should contains error message: ${error.replace('PART',partition).replace('POOL', namePool).replace('MEMBER', nameMember)}")
//                outputParameters.poolMemberDelete == error
//                        .replace('PART', partition)
//                        .replace('POOL', namePool)
//                        .replace('MEMBER', nameMember)
                assert outputParameters.poolMemberDelete == null

//                testCaseHelper.addExpectedResult("property $propertyName should contains error message: ${error.replace('PART',partition).replace('POOL', namePool).replace('MEMBER', nameMember)}")
//                propertyName == error
//                        .replace('PART', partition)
//                        .replace('POOL', namePool)
//                        .replace('MEMBER', nameMember)
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
        caseId     | configName    | nameMember    | partition     | namePool       | resultPropertySheet       | expectedSummary                     | expectedLog                      | error
        TC.C387585 | ''            | "1.1.1.1:80"  | 'Common'      | 'testPoolName' | '/myJob/poolMemberDelete' | expectedSummaries.emptyConfig       | expectedSummaries.emptyConfig    | ''
        TC.C387588 | CONFIG_NAME   | ""            | 'Common'      | 'testPoolName' | '/myJob/poolMemberDelete' | expectedSummaries.emptyMemberName   | expectedSummaries.emptyMemberName| ''
        TC.C387586 | CONFIG_NAME   | "1.1.1.1:80"  | ''            | 'testPoolName' | '/myJob/poolMemberDelete' | expectedSummaries.emptyPartition    | expectedSummaries.emptyPartition | ''
        TC.C387587 | CONFIG_NAME   | "1.1.1.1:80"  | 'Common'      | ''             | '/myJob/poolMemberDelete' | expectedSummaries.emptyPoolName     | expectedSummaries.emptyPoolName  | ''
        TC.C387589 | CONFIG_NAME   | "1.1.1.1:80"  | 'Common'      | 'testPoolName' | ''                        | expectedSummaries.emptyResult       | expectedSummaries.emptyResult    | ''
        TC.C387590 | 'nonExistent' | "1.1.1.1:80"  | 'Common'      | 'testPoolName' | '/myJob/poolMemberDelete' | expectedSummaries.nonexisConfig     | expectedSummaries.nonexisConfig  | ''
        TC.C387593 | CONFIG_NAME   | "nonExistent" | 'Common'      | 'testPoolName' | '/myJob/poolMemberDelete' | expectedSummaries.nonexistentMember | expectedLogs.deleted             | outputErrors.poolMember
        TC.C387592 | CONFIG_NAME   | "1.1.1.1:80"  | 'nonExistent' | 'testPoolName' | '/myJob/poolMemberDelete' | expectedSummaries.nonexistPartition | expectedLogs.deleted             | outputErrors.memberNotFound
        TC.C387591 | CONFIG_NAME   | "1.1.1.1:80"  | 'Common'      | 'nonExistent'  | '/myJob/poolMemberDelete' | expectedSummaries.nonexisPoolName   | expectedLogs.deleted             | outputErrors.memberNotFound
//        TC.C387594 | CONFIG_NAME   | "1.1.1.1:80"  | 'Common'      | 'testPoolName' | '/invalid/invalid'        | expectedSummaries.wrongResult       | expectedLogs.deleted             | ''
        TC.C387594 | CONFIG_NAME   | "1.1.1.1:80"  | 'Common'      | 'testPoolName' | '/invalid/invalid'        | expectedSummaries.nonexisPoolName   | expectedLogs.deleted             | ''
    }
}
