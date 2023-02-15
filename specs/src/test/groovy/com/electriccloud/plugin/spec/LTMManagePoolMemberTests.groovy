package com.electriccloud.plugin.spec

import com.electriccloud.plugins.annotations.NewFeature
import com.electriccloud.plugins.annotations.Sanity
import spock.lang.*
import groovy.json.JsonSlurper

@Ignore
// test suite is deprecated
// http://jira.electric-cloud.com/browse/ECPBIGIP-47
class LTMManagePoolMemberTests extends PluginTestHelper {

    static def projectName = 'TestProject: Manage Pool Member'
    static def configurationName = 'configuration1'
    static def bigIpClient

    static def TC = [
            C387530: [ids: 'C387530', description: 'Manage Pool Member - replace'],
            C387531: [ids: 'C387531', description: 'Manage Pool Member - update'],

            C387532: [ids: 'C387532', description: 'empty Config'],
            C387533: [ids: 'C387533', description: 'empty Method'],
            C387534: [ids: 'C387534', description: 'empty Partition Name'],
            C387535: [ids: 'C387535', description: 'empty Pool Name'],
            C387544: [ids: 'C387544', description: 'empty Member Name'],
            C387545: [ids: 'C387545', description: 'empty Result Property Sheet'],
            C387546: [ids: 'C387546', description: 'wrong Config'],
            C387549: [ids: 'C387549', description: 'wrong Method'],
            C387550: [ids: 'C387550', description: 'wrong Pool'],
            C387551: [ids: 'C387551', description: 'wrong Name'],
            C387553: [ids: 'C387553', description: 'wrong Partition'],
    ]

    static def expectedSummaries = [
            replaced      : 'Pool member Common~POOL~NAME has been replaced',
            updated       : 'Pool member Common~POOL~NAME has been updated',
            emptyConfig   : "No config name",
            emptyMethod   : 'Required parameter "method" is missing',
            emptyPartition: 'Required parameter "partition" is missing',
            emptyPool     : 'Required parameter "pool_name" is missing',
            emptyName     : 'Required parameter "name" is missing',
            emptyResult   : 'Required parameter "resultPropertySheet" is missing',
            wrongPartition: 'The requested Pool Member (/wrong/test_5679 /wrong/10.200.1.137 80) was not found.',
            wrongName     : 'The requested Pool Member (/Common/test_5679 /Common/wrong 80) was not found.',
            wrongPool     : "The requested Pool Member (/Common/wrong /Common/10.200.1.137 80) was not found.",
            wrongMethod   : 'Wrong method value: wrong',
            wrongConfig   : 'Configuration "wrongConfig" does not exist',

    ]

    static def expectedLogs = [
            replaced: "PUT ${BIGIP_LOCAL_PROTOCOL}://${BIGIP_LOCAL_HOST}:${BIGIP_LOCAL_PORT}/mgmt/tm/ltm/pool/~Common~POOL/members",
            updated : "PATCH ${BIGIP_LOCAL_PROTOCOL}://${BIGIP_LOCAL_HOST}:${BIGIP_LOCAL_PORT}/mgmt/tm/ltm/pool/~Common~POOL/members",
    ]

    static def outputErrors = [
            pool: '{"code":404,"message":"01020036:3: The requested Pool Member (/Common/wrong /Common/10.200.1.137 80) was not found.","errorStack":[],"apiError":3}',
            name: '{"code":404,"message":"01020036:3: The requested Pool Member (/Common/test_5679 /Common/wrong 80) was not found.","errorStack":[],"apiError":3}',
            partition: '{"code":404,"message":"01020036:3: The requested Pool Member (/wrong/test_5679 /wrong/10.200.1.137 80) was not found.","errorStack":[],"apiError":3}',
    ]


    static def defaultPoolName = 'test_5679'
    static def defaultName = '10.200.1.137:80'
    static def defaultPartition = 'Common'

    static def testCaseHelper

    def doSetupSpec() {
        bigIpClient = getBigIpHelper()
        testCaseHelper = new TestCaseHelper(managePoolMember)
        createConfiguration(configurationName)
        dslFile "dsl/RunProcedure.dsl", [projectName: projectName, resName: 'local', procedureName: createPoolMember, params: createPoolMemberParams]
        dslFile "dsl/RunProcedure.dsl", [projectName: projectName, resName: 'local', procedureName: managePoolMember, params: managePoolMemberParams]

    }

    def doCleanupSpec() {
        testCaseHelper.createTestCases()
        conditionallyDeleteProject(projectName)
        deleteConfiguration(PLUGIN_NAME, configurationName)
    }

    @Sanity
    @Unroll
    def 'ManagePoolMember: Sanity #caseId.ids #caseId.description'() {
        testCaseHelper.createNewTestCase(caseId.ids, caseId.description)

        testCaseHelper.testCasePrecondition("Create Balancing Pool with name $poolName")
        bigIpClient.createBalancingPool(partition, poolName)

        testCaseHelper.testCasePrecondition("Create pool member with name $name which will be updated, with optionalParameters:")
        testCaseHelper.testCasePrecondition("rateLimit=3;description=firstDescription")
        def preconditionParams = [
                config             : configName,
                method             : '0',
                name               : defaultName,
                optionalParameters : 'rateLimit=3;description=firstDescription',
                partition          : defaultPartition,
                pool_name          : defaultPoolName,
                resultFormat       : resultFormat,
                resultPropertySheet: resultPropertySheet,
        ]
        runProcedure(projectName, createPoolMember, preconditionParams)
        def preconditionPoolMemberInfo = bigIpClient.getPoolMemberInfo(poolName, name)


        given: "Tests parameters for procedure LTM Manage Pool Member"
        def parameters = [
                config             : configName,
                method             : method,
                name               : defaultName,
                optionalParameters : optionalParameters,
                partition          : defaultPartition,
                pool_name          : defaultPoolName,
                resultFormat       : resultFormat,
                resultPropertySheet: resultPropertySheet,
        ]

        when: "Run procedure"
        def result = runProcedure(projectName, managePoolMember, parameters)
        def jobSummary = getJobProperty("/myJob/jobSteps/$managePoolMember/summary", result.jobId)

        def outputParameters = getJobOutputParameters(result.jobId, 1)
        def jobProperties = getJobProperties(result.jobId)

        def poolMemberInfo = bigIpClient.getPoolMemberInfo(poolName, name)
        poolMemberInfo.items[0].selfLink = poolMemberInfo.items[0].selfLink.replace(poolName, "~$partition~$poolName")
        preconditionPoolMemberInfo.items[0].selfLink = preconditionPoolMemberInfo.items[0].selfLink.replace(poolName, "~$partition~$poolName")

        def propertyName = jobProperties[resultPropertySheet.split("/")[2]]
        then: "Verify results"
        verifyAll {
            result.outcome == 'success'

            jobSummary.contains(expectedSummary
                    .replace('POOL', poolName)
                    .replace('NAME', name))

            result.logs.contains(expectedLog
                    .replace('POOL', poolName)
                    .replace('NAME', name))

            new JsonSlurper().parseText(propertyName) == poolMemberInfo.items[0]

            new JsonSlurper().parseText(outputParameters.poolMemberManage) == poolMemberInfo.items[0]

            if (method == 'PUT') {
                preconditionPoolMemberInfo.items[0].rateLimit != poolMemberInfo.items[0].rateLimit
            } else if (method == 'PATCH') {
                preconditionPoolMemberInfo.items[0].rateLimit == poolMemberInfo.items[0].rateLimit
            }

            if (optionalParameters) {
                for (param in optionalParameters.split(";")) {
                    poolMemberInfo.items[0][param.split("=")[0]] == param.split("=")[1]

                    poolMemberInfo.items[0][param.split("=")[0]] != preconditionPoolMemberInfo.items[0][param.split("=")[0]]
                }
            }

            poolMemberInfo.items[0]
            poolMemberInfo.items[0].name == name
            poolMemberInfo.items[0].partition == partition
        }

        cleanup:
        try {
            bigIpClient.deleteBalancingPool(partition, poolName)
            bigIpClient.deleteNode(partition, name)
        } catch (Throwable e) {
            logger.debug(e.message)
        }

        where:
        caseId     | configName        | method   | name        | optionalParameters            | poolName        | partition         | resultFormat | resultPropertySheet       | expectedSummary            | expectedLog
        TC.C387530 | configurationName | 'PUT'    | defaultName | 'description=newDescription'  | defaultPoolName | defaultPartition  | 'json'       | '/myJob/poolMemberManage' | expectedSummaries.replaced | expectedLogs.replaced
        TC.C387531 | configurationName | 'PATCH'  | defaultName | 'description=newDescription'  | defaultPoolName | defaultPartition  | 'json'       | '/myJob/poolMemberManage' | expectedSummaries.updated  | expectedLogs.updated
    }


    @NewFeature(pluginVersion = "3.0.0")
    @Unroll
    def 'ManagePoolMember: Positive #caseId.ids #caseId.description'() {
        testCaseHelper.createNewTestCase(caseId.ids, caseId.description)

        testCaseHelper.testCasePrecondition("Create Balancing Pool with name $poolName")
        bigIpClient.createBalancingPool(partition, poolName)

        testCaseHelper.testCasePrecondition("Create pool member with name $name which will be updated, with optionalParameters:")
        testCaseHelper.testCasePrecondition("rateLimit=3;description=firstDescription")
        def preconditionParams = [
                config             : configName,
                method             : '0',
                name               : defaultName,
                optionalParameters : 'rateLimit=3;description=firstDescription',
                partition          : defaultPartition,
                pool_name          : defaultPoolName,
                resultFormat       : resultFormat,
                resultPropertySheet: resultPropertySheet,
        ]
        runProcedure(projectName, createPoolMember, preconditionParams)
        def preconditionPoolMemberInfo = bigIpClient.getPoolMemberInfo(poolName, name)


        given: "Tests parameters for procedure LTM Manage Pool Member"
        def parameters = [
                config             : configName,
                method             : method,
                name               : defaultName,
                optionalParameters : optionalParameters,
                partition          : defaultPartition,
                pool_name          : defaultPoolName,
                resultFormat       : resultFormat,
                resultPropertySheet: resultPropertySheet,
        ]
        testCaseHelper.addStepContent("Run procedure $managePoolMember with parameters:", parameters)

        when: "Run procedure"
        def result = runProcedure(projectName, managePoolMember, parameters)
        def jobSummary = getJobProperty("/myJob/jobSteps/$managePoolMember/summary", result.jobId)

        def outputParameters = getJobOutputParameters(result.jobId, 1)
        def jobProperties = getJobProperties(result.jobId)

        def poolMemberInfo = bigIpClient.getPoolMemberInfo(poolName, name)
        poolMemberInfo.items[0].selfLink = poolMemberInfo.items[0].selfLink.replace(poolName, "~$partition~$poolName")
        preconditionPoolMemberInfo.items[0].selfLink = preconditionPoolMemberInfo.items[0].selfLink.replace(poolName, "~$partition~$poolName")

        def propertyName = jobProperties[resultPropertySheet.split("/")[2]]
        then: "Verify results"
        verifyAll {
            testCaseHelper.addExpectedResult("Job status: success")
            result.outcome == 'success'

            testCaseHelper.addExpectedResult("Job Summary: ${expectedSummary.replace('POOL', poolName).replace('NAME', name)}")
            jobSummary.contains(expectedSummary
                    .replace('POOL', poolName)
                    .replace('NAME', name))

            testCaseHelper.addExpectedResult("Job Logs: $expectedLog")
            result.logs.contains(expectedLog
                    .replace('POOL', poolName)
                    .replace('NAME', name))

            testCaseHelper.addExpectedResult("Job Properties:")
            testCaseHelper.addExpectedResult("1. $resultPropertySheet: $propertyName")
            new JsonSlurper().parseText(propertyName) == poolMemberInfo.items[0]

            testCaseHelper.addExpectedResult("Job OutputParameters:")
            testCaseHelper.addExpectedResult("1. poolMemberCreate: ${outputParameters.poolMemberManage}")
            new JsonSlurper().parseText(outputParameters.poolMemberManage) == poolMemberInfo.items[0]

            if (method == 'PUT') {
                testCaseHelper.addExpectedResult("BigIp: member pool $name should be recrated in pool $poolName, old values shouldn't be saved")
                preconditionPoolMemberInfo.items[0].rateLimit != poolMemberInfo.items[0].rateLimit
            } else if (method == 'PATCH') {
                testCaseHelper.addExpectedResult("BigIp: member pool $name should be update in pool $poolName, old values should be saved")
                preconditionPoolMemberInfo.items[0].rateLimit == poolMemberInfo.items[0].rateLimit
            }

            if (optionalParameters) {
                for (param in optionalParameters.split(";")) {
                    testCaseHelper.addExpectedResult("BigIp: member pool $name should have option ${param.split("=")[0]} with value ${param.split("=")[1]}")
                    poolMemberInfo.items[0][param.split("=")[0]] == param.split("=")[1]

                    testCaseHelper.addExpectedResult("member parameter ${param.split("=")[0]} should be updated from " +
                            "from ${preconditionPoolMemberInfo.items[0][param.split("=")[0]]} to ${poolMemberInfo.items[0][param.split("=")[0]]}")
                    poolMemberInfo.items[0][param.split("=")[0]] != preconditionPoolMemberInfo.items[0][param.split("=")[0]]
                }
            }

            testCaseHelper.addExpectedResult("member pool json: ${poolMemberInfo.items[0]}")
            poolMemberInfo.items[0]
            poolMemberInfo.items[0].name == name
            poolMemberInfo.items[0].partition == partition
        }

        cleanup:
        try {
            bigIpClient.deleteBalancingPool(partition, poolName)
            bigIpClient.deleteNode(partition, name)
        } catch (Throwable e) {
            logger.debug(e.message)
        }

        where:
        caseId     | configName        | method   | name        | optionalParameters            | poolName        | partition         | resultFormat | resultPropertySheet       | expectedSummary            | expectedLog
        TC.C387530 | configurationName | 'PUT'    | defaultName | 'description=newDescription'  | defaultPoolName | defaultPartition  | 'json'       | '/myJob/poolMemberManage' | expectedSummaries.replaced | expectedLogs.replaced
        TC.C387531 | configurationName | 'PATCH'  | defaultName | 'description=newDescription'  | defaultPoolName | defaultPartition  | 'json'       | '/myJob/poolMemberManage' | expectedSummaries.updated  | expectedLogs.updated
    }

    @NewFeature(pluginVersion = "3.0.0")
    @Unroll
    def 'ManagePoolMember: Negative #caseId.ids #caseId.description'() {
        testCaseHelper.createNewTestCase(caseId.ids, caseId.description)

        given: "Tests parameters for procedure LTM Manage Pool Member"
        def parameters = [
                config             : configName,
                method             : method,
                name               : name,
                optionalParameters : optionalParameters,
                partition          : partition,
                pool_name          : poolName,
                resultFormat       : resultFormat,
                resultPropertySheet: resultPropertySheet,
        ]
        testCaseHelper.addStepContent("Run procedure $managePoolMember with parameters:", parameters)

        when: "Run procedure"
        def result = runProcedure(projectName, managePoolMember, parameters)
        def jobSummary = getJobProperty("/myJob/jobSteps/$managePoolMember/summary", result.jobId)

        def outputParameters = getJobOutputParameters(result.jobId, 1)
        def jobProperties = getJobProperties(result.jobId)

        def propertyName
        if (resultPropertySheet) {
            propertyName = jobProperties[resultPropertySheet.split("/")[2]]
        }
        then: "Verify results"
        verifyAll {
            testCaseHelper.addExpectedResult("Job status: error")
            result.outcome == 'error'

            testCaseHelper.addExpectedResult("Job Summary: ${expectedSummary.replace('POOL', poolName).replace('NAME', name)}")
            jobSummary.contains(expectedSummary
                    .replace('POOL', poolName)
                    .replace('NAME', name))

            testCaseHelper.addExpectedResult("Job Logs: $expectedLog")
            result.logs.contains(expectedLog
                    .replace('POOL', poolName))

            if (error){
                testCaseHelper.addExpectedResult("Output parameter should contains error message: $error")
                outputParameters.poolMemberManage == error
                testCaseHelper.addExpectedResult("property $propertyName should contains error message: $error")
                propertyName == error
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
        caseId     | configName        | method   | name        | optionalParameters | poolName        | partition         | resultFormat | resultPropertySheet       | expectedSummary                  | expectedLog                          | error
        TC.C387532 | ''                | 'PUT'    | defaultName | ''                 | defaultPoolName | defaultPartition  | 'json'       | '/myJob/poolMemberManage' | expectedSummaries.emptyConfig    | expectedSummaries.emptyConfig        | ''
        TC.C387533 | configurationName | ''       | defaultName | ''                 | defaultPoolName | defaultPartition  | 'json'       | '/myJob/poolMemberManage' | expectedSummaries.emptyMethod    | expectedSummaries.emptyMethod        | ''
        TC.C387534 | configurationName | 'PUT'    | defaultName | ''                 | defaultPoolName | ''                | 'json'       | '/myJob/poolMemberManage' | expectedSummaries.emptyPartition | expectedSummaries.emptyPartition     | ''
        TC.C387535 | configurationName | 'PUT'    | defaultName | ''                 | ''              | defaultPartition  | 'json'       | '/myJob/poolMemberManage' | expectedSummaries.emptyPool      | expectedSummaries.emptyPool          | ''
        TC.C387544 | configurationName | 'PUT'    | ''          | ''                 | defaultPoolName | defaultPartition  | 'json'       | '/myJob/poolMemberManage' | expectedSummaries.emptyName      | expectedSummaries.emptyName          | ''
        TC.C387545 | configurationName | 'PUT'    | defaultName | ''                 | defaultPoolName | defaultPartition  | 'json'       | ''                        | expectedSummaries.emptyResult    | expectedSummaries.emptyResult        | ''
        TC.C387546 | 'wrongConfig'     | 'PUT'    | defaultName | ''                 | defaultPoolName | defaultPartition  | 'json'       | '/myJob/poolMemberManage' | expectedSummaries.wrongConfig    | expectedSummaries.wrongConfig        | ''
        TC.C387549 | configurationName | 'wrong'  | defaultName | ''                 | defaultPoolName | defaultPartition  | 'json'       | '/myJob/poolMemberManage' | expectedSummaries.wrongMethod    | expectedSummaries.wrongMethod        | ''
        TC.C387550 | configurationName | 'PUT'    | defaultName | ''                 | 'wrong'         | defaultPartition  | 'json'       | '/myJob/poolMemberManage' | expectedSummaries.wrongPool      | expectedSummaries.wrongPool          | outputErrors.pool
        TC.C387551 | configurationName | 'PUT'    | 'wrong:80'  | ''                 | defaultPoolName | defaultPartition  | 'json'       | '/myJob/poolMemberManage' | expectedSummaries.wrongName      | expectedSummaries.wrongName          | outputErrors.name
        TC.C387553 | configurationName | 'PUT'    | defaultName | ''                 | defaultPoolName | 'wrong'           | 'json'       | '/myJob/poolMemberManage' | expectedSummaries.wrongPartition | expectedSummaries.wrongPartition     | outputErrors.partition

    }
}