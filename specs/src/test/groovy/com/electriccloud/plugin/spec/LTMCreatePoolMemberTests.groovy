package com.electriccloud.plugin.spec

import com.electriccloud.plugins.annotations.NewFeature
import com.electriccloud.plugins.annotations.Sanity
import spock.lang.*
import groovy.json.JsonSlurper

//@Ignore
class LTMCreatePoolMemberTests extends PluginTestHelper {

    static def projectName = 'TestProject: CreatePoolMember'
    static def configurationName = 'configuration1'
    static def bigIpClient

    static def TC = [
            C387263: [ids: 'C387263', description: 'Create pool member - all required parameters'],
            C387269: [ids: 'C387269', description: 'Create pool member with one optionalParameter'],
            C387278: [ids: 'C387278', description: 'Create pool member with some optionalParameters'],
            C387282: [ids: 'C387282', description: 'Create pool member - "Update Action" - Remove and Create, method - PUT'],
            C387284: [ids: 'C387284', description: 'Create pool member - "Update Action" - Selective Update, method - PATCH'],
            C387285: [ids: 'C387285', description: 'Create pool member - "Update Action" - Selective Update, method - PATCH with some optionalParameters'],
            C387636: [ids: 'C387636', description: 'Create pool member - "Update Action" - Throw exception - ERROR'],

            C387279: [ids: 'C387279', description: 'Update pool member: "Update Action" - Remove and Create, method - PUT'],
            C387280: [ids: 'C387280', description: 'Update pool member: "Update Action" - Selective Update, method - PATCH'],
            C387281: [ids: 'C387281', description: 'Update pool member: "Update Action" - Do Nothing, method - 0'],
            C387637: [ids: 'C387637', description: 'Update pool member: "Update Action" - Throw exception - ERROR'],


//            deprecated
//            C387636: [ids: 'C387636', description: 'Update pool member: "Update Action" - Do Nothing, method - 0, if not exists - throw exception  '],
//            C387637: [ids: 'C387637', description: 'Update pool member: "Update Action" - Remove and Create, method - PUT, if not exists - throw exception  '],
//            C387638: [ids: 'C387638', description: 'Update pool member: "Update Action" - Selective Update, method - PATCH, if not exists - throw exception  '],


            C387286: [ids: 'C387286', description: 'Create pool members in pool which already has pool members'],
            C387287: [ids: 'C387287', description: 'Create pool members, some pools exist'],

            C387288: [ids: 'C387288', description: 'empty Config'],
            C387289: [ids: 'C387289', description: 'empty Method'],
            C387290: [ids: 'C387290', description: 'empty Partition Name'],
            C387291: [ids: 'C387291', description: 'empty Pool Name'],
            C387292: [ids: 'C387292', description: 'empty Member Name'],
            C387293: [ids: 'C387293', description: 'empty Result Property Sheet'],
            C387294: [ids: 'C387294', description: 'wrong Config'],
            C387295: [ids: 'C387295', description: 'wrong Method'],
            C387296: [ids: 'C387296', description: 'wrong Pool'],
            C387297: [ids: 'C387297', description: 'wrong Name'],
            C387298: [ids: 'C387298', description: 'wrong Partition'],

//            deprecated
//            C387640: [ids: 'C387640', description: 'Pool member doesn`t exist: "If exists" - Do Nothing, method - 0, if not exists - throw exception  '],
//            C387641: [ids: 'C387641', description: 'Pool member doesn`t exist: "If exists" - Remove and Create, method - PUT, if not exists - throw exception  '],
//            C387642: [ids: 'C387642', description: 'Pool member doesn`t exist: "If exists" - Selective Update, method - PATCH, if not exists - throw exception  '],
    ]

    static def expectedSummaries = [
            default       : 'Pool member Common~POOL~NAME has been created',
            replaced      : 'Pool member Common~POOL~NAME has been replaced',
            updated       : 'Pool member Common~POOL~NAME has been updated',
            notUpdated    : "Pool member Common~POOL~NAME hasn't been touched",
            emptyConfig   : "No config name",
            emptyMethod   : 'Required parameter "updateAction" is missing',
            emptyPartition: 'Required parameter "partition" is missing',
            emptyPool     : 'Required parameter "pool_name" is missing',
            emptyName     : 'Required parameter "name" is missing',
            emptyResult   : 'Required parameter "resultPropertySheet" is missing',
            wrongPartition: "Configuration error: Can't associate Pool Member (/wrong/test_5679 /wrong/10.200.1.137 http) folder does not exist",
            wrongName     : 'HTML Tag-like Content in the Request URL/Body',
            wrongPool     : 'The requested pool (/Common/wrong) was not found.',
            wrongMethod   : 'Wrong method value: wrong',
            wrongConfig   : 'Configuration "wrongConfig" does not exist',
            objectNotFound: 'Object not found - /Common/NAME',
            throwError    : 'Pool member Common~POOL~NAME has been found'

    ]

    static def expectedLogs = [
            default : "POST ${bigIpProtocol}://${bigIpHost}:${bigIpPort}/mgmt/tm/ltm/pool/~Common~POOL/members",
            replaced: "PUT ${bigIpProtocol}://${bigIpHost}:${bigIpPort}/mgmt/tm/ltm/pool/~Common~POOL/members",
            updated : "PATCH ${bigIpProtocol}://${bigIpHost}:${bigIpPort}/mgmt/tm/ltm/pool/~Common~POOL/members",
            wrongName: '400 Bad Request'
    ]

    static def outputErrors = [
            pool: '{"code":404,"message":"01020036:3: The requested pool (/Common/wrong) was not found.","errorStack":[],"apiError":3}',
            name: '{"code":400,"message":"HTML Tag-like Content in the Request URL/Body","errorStack":[],"apiError":26214401}',
            partition: '{"code":400,"message":"01070734:3: Configuration error: Can\'t associate Pool Member (/wrong/test_5679 /wrong/10.200.1.137 http) folder does not exist","errorStack":[],"apiError":3}',
            notFound: '{"code":404,"message":"Object not found - /Common/10.200.1.137:80","errorStack":[],"apiError":1}'
    ]

    static def testCaseHelper

    def doSetupSpec() {
        bigIpClient = getBigIpHelper()
        testCaseHelper = new TestCaseHelper(createPoolMember)
        createConfiguration(configurationName)
        dslFile "dsl/RunProcedure.dsl", [projectName: projectName, resName: 'local', procedureName: createPoolMember, params: createPoolMemberParams]
    }

    def doCleanupSpec() {
        testCaseHelper.createTestCases()
        conditionallyDeleteProject(projectName)
        deleteConfiguration(PLUGIN_NAME, configurationName)
    }

    @Sanity
    @Unroll
    def 'CreatePoolMember: Sanity Positive #caseId.ids #caseId.description'() {
        bigIpClient.createBalancingPool(partition, poolName)
        given: "Tests parameters for procedure LTM CreatePoolMemberTests"
        def runImageParams = [
                config             : configName,
                updateAction       : method,
                name               : name,
                optionalParameters : optionalParameters,
                partition          : partition,
                pool_name          : poolName,
                resultPropertySheet: resultPropertySheet,
        ]

        when: "Run procedure"
        def result = runProcedure(projectName, createPoolMember, runImageParams)
        def jobSummary = getJobProperty("/myJob/jobSteps/$createPoolMember/summary", result.jobId)

        def outputParameters = getJobOutputParameters(result.jobId, 1)
        def jobProperties = getJobProperties(result.jobId)

        def poolMemberInfo = bigIpClient.getPoolMemberInfo(poolName, name)
        poolMemberInfo.items[0].selfLink = poolMemberInfo.items[0].selfLink.replace(poolName, "~$partition~$poolName")

        def propertyName = jobProperties[resultPropertySheet.split("/")[2]]
        then: "Verify results"
        verifyAll {
            result.outcome == 'success'

            jobSummary.contains(expectedSummary
                    .replace('POOL', poolName)
                    .replace('NAME', name))

            result.logs.contains(expectedLog
                    .replace('POOL', poolName))

            new JsonSlurper().parseText(propertyName) == poolMemberInfo.items[0]

            new JsonSlurper().parseText(outputParameters.poolMemberCreateOrUpdate) == poolMemberInfo.items[0]

            if (optionalParameters) {
                for (param in optionalParameters.split(";")) {
                    poolMemberInfo.items[0][param.split("=")[0]] == param.split("=")[1]
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
        caseId     | configName        | method  | name              | optionalParameters                | poolName    | partition | resultPropertySheet               | expectedSummary           | expectedLog
        TC.C387263 | configurationName | '0'     | '10.200.1.137:80' | ''                                | 'test_5679' | 'Common'  | '/myJob/poolMemberCreateOrUpdate' | expectedSummaries.default | expectedLogs.default
        TC.C387285 | configurationName | 'PATCH' | '10.200.1.137:80' | 'rateLimit=2;description=test123' | 'test_5679' | 'Common'  | '/myJob/poolMemberCreateOrUpdate' | expectedSummaries.default | expectedLogs.default
    }

    @NewFeature(pluginVersion = "3.0.0")
    @Unroll
    def 'CreatePoolMember: Positive #caseId.ids #caseId.description'() {
        testCaseHelper.createNewTestCase(caseId.ids, caseId.description)

        testCaseHelper.testCasePrecondition("Create Balancing Pool with name $poolName")
        bigIpClient.createBalancingPool(partition, poolName)
        given: "Tests parameters for procedure LTM CreatePoolMemberTests"
        def runImageParams = [
                config             : configName,
                updateAction       : method,
                name               : name,
                optionalParameters : optionalParameters,
                partition          : partition,
                pool_name          : poolName,
                resultPropertySheet: resultPropertySheet,
        ]
        testCaseHelper.addStepContent("Run procedure $createPoolMember with parameters:", runImageParams)

        when: "Run procedure"
        def result = runProcedure(projectName, createPoolMember, runImageParams)
        def jobSummary = getJobProperty("/myJob/jobSteps/$createPoolMember/summary", result.jobId)

        def outputParameters = getJobOutputParameters(result.jobId, 1)
        def jobProperties = getJobProperties(result.jobId)

        def poolMemberInfo = bigIpClient.getPoolMemberInfo(poolName, name)
        poolMemberInfo.items[0].selfLink = poolMemberInfo.items[0].selfLink.replace(poolName, "~$partition~$poolName")

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
                    .replace('POOL', poolName))

            testCaseHelper.addExpectedResult("Job Properties:")
            testCaseHelper.addExpectedResult("1. $resultPropertySheet: $propertyName")
            new JsonSlurper().parseText(propertyName) == poolMemberInfo.items[0]

            testCaseHelper.addExpectedResult("Job OutputParameters:")
            testCaseHelper.addExpectedResult("1. poolMemberCreate: ${outputParameters.poolMemberCreateOrUpdate}")
            new JsonSlurper().parseText(outputParameters.poolMemberCreateOrUpdate) == poolMemberInfo.items[0]

            testCaseHelper.addExpectedResult("BigIp: member pool $name should be created in pool $poolName")

            if (optionalParameters) {
                for (param in optionalParameters.split(";")) {
                    testCaseHelper.addExpectedResult("BigIp: member pool $name should have option ${param.split("=")[0]} with value ${param.split("=")[1]}")
                    poolMemberInfo.items[0][param.split("=")[0]] == param.split("=")[1]
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
        caseId     | configName        | method  | name              | optionalParameters                | poolName    | partition | resultPropertySheet               | expectedSummary           | expectedLog
        TC.C387263 | configurationName | '0'     | '10.200.1.137:80' | ''                                | 'test_5679' | 'Common'  | '/myJob/poolMemberCreateOrUpdate' | expectedSummaries.default | expectedLogs.default
        TC.C387269 | configurationName | '0'     | '10.200.1.249:80' | 'description=test123'             | 'test_5680' | 'Common'  | '/myJob/poolMemberCreateOrUpdate' | expectedSummaries.default | expectedLogs.default
        TC.C387278 | configurationName | '0'     | '10.200.1.249:80' | 'rateLimit=2;description=test123' | 'test_5680' | 'Common'  | '/myJob/poolMemberCreateOrUpdate' | expectedSummaries.default | expectedLogs.default
        TC.C387282 | configurationName | 'PUT'   | '10.200.1.137:80' | ''                                | 'test_5679' | 'Common'  | '/myJob/poolMemberCreateOrUpdate' | expectedSummaries.default | expectedLogs.default
        TC.C387284 | configurationName | 'PATCH' | '10.200.1.137:80' | ''                                | 'test_5679' | 'Common'  | '/myJob/poolMemberCreateOrUpdate' | expectedSummaries.default | expectedLogs.default
        TC.C387285 | configurationName | 'PATCH' | '10.200.1.137:80' | 'rateLimit=2;description=test123' | 'test_5679' | 'Common'  | '/myJob/poolMemberCreateOrUpdate' | expectedSummaries.default | expectedLogs.default
        TC.C387636 | configurationName | 'ERROR' | '10.200.1.137:80' | ''                                | 'test_5679' | 'Common'  | '/myJob/poolMemberCreateOrUpdate' | expectedSummaries.default | expectedLogs.default
    }

    @NewFeature(pluginVersion = "3.0.0")
    @Unroll
    def 'CreatePoolMember: Update pool member Positive #caseId.ids #caseId.description'() {
        testCaseHelper.createNewTestCase(caseId.ids, caseId.description)

        testCaseHelper.testCasePrecondition("Create Balancing Pool with name $poolName")
        bigIpClient.createBalancingPool(partition, poolName)

        testCaseHelper.testCasePrecondition("Create pool member with name $name which will be updated, with optionalParameters:")
        testCaseHelper.testCasePrecondition("rateLimit=3;description=firstDescription")
        def preconditionParams = [
                config             : configName,
                updateAction       : method,
                name               : name,
                optionalParameters : 'rateLimit=3;description=firstDescription',
                partition          : partition,
                pool_name          : poolName,
                resultPropertySheet: resultPropertySheet,
        ]
        runProcedure(projectName, createPoolMember, preconditionParams)
        def preconditionPoolMemberInfo = bigIpClient.getPoolMemberInfo(poolName, name)

        given: "Tests parameters for procedure LTM CreatePoolMemberTests"
        def runImageParams = [
                config             : configName,
                updateAction       : method,
                name               : name,
                optionalParameters : optionalParameters,
                partition          : partition,
                pool_name          : poolName,
                resultPropertySheet: resultPropertySheet,
        ]
        testCaseHelper.addStepContent("Run procedure $createPoolMember with parameters:", runImageParams)

        when: "Run procedure"
        def result = runProcedure(projectName, createPoolMember, runImageParams)
        def jobSummary = getJobProperty("/myJob/jobSteps/$createPoolMember/summary", result.jobId)

        def outputParameters = getJobOutputParameters(result.jobId, 1)
        def jobProperties = getJobProperties(result.jobId)

        def poolMemberInfo = bigIpClient.getPoolMemberInfo(poolName, name)
        poolMemberInfo.items[0].selfLink = poolMemberInfo.items[0].selfLink.replace(poolName, "~$partition~$poolName")
        preconditionPoolMemberInfo.items[0].selfLink = preconditionPoolMemberInfo.items[0].selfLink.replace(poolName, "~$partition~$poolName")


        def propertyName = jobProperties[resultPropertySheet.split("/")[2]]
        then: "Verify results"
        verifyAll {
            testCaseHelper.addExpectedResult("Job status: $outcome")
            result.outcome == outcome

            testCaseHelper.addExpectedResult("Job Summary: ${expectedSummary.replace('POOL', poolName).replace('NAME', name)}")
            jobSummary.contains(expectedSummary
                    .replace('POOL', poolName)
                    .replace('NAME', name))

            testCaseHelper.addExpectedResult("Job Logs: $expectedLog")
            result.logs.contains(expectedLog
                    .replace('POOL', poolName)
                    .replace('NAME', name))

            def error = (outcome == 'error')

            if (error) {
                assert propertyName == null
            } else {
                testCaseHelper.addExpectedResult("Job Properties:")
                testCaseHelper.addExpectedResult("1. $resultPropertySheet: $propertyName")
                new JsonSlurper().parseText(propertyName) == poolMemberInfo.items[0]
            }

            if (error) {
                assert outputParameters.poolMemberCreateOrUpdate == null
            } else {
                testCaseHelper.addExpectedResult("Job OutputParameters:")
                testCaseHelper.addExpectedResult("1. poolMemberCreate: ${outputParameters.poolMemberCreateOrUpdate}")
                new JsonSlurper().parseText(outputParameters.poolMemberCreateOrUpdate) == poolMemberInfo.items[0]
            }


            if (method in ['0', 'ERROR']) {
                testCaseHelper.addExpectedResult("BigIp: member pool $name shouldn't be updated, old values should be saved")
                preconditionPoolMemberInfo.items[0] == poolMemberInfo.items[0]
            } else {
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
        caseId     | configName        | method  | name              | optionalParameters           | poolName    | partition | resultPropertySheet               | outcome   | expectedSummary              | expectedLog
        TC.C387279 | configurationName | 'PUT'   | '10.200.1.137:80' | 'description=newDescription' | 'test_5679' | 'Common'  | '/myJob/poolMemberCreateOrUpdate' | 'success' | expectedSummaries.replaced   | expectedLogs.replaced
        TC.C387280 | configurationName | 'PATCH' | '10.200.1.137:80' | 'description=newDescription' | 'test_5679' | 'Common'  | '/myJob/poolMemberCreateOrUpdate' | 'success' | expectedSummaries.updated    | expectedLogs.updated
        TC.C387281 | configurationName | '0'     | '10.200.1.137:80' | 'description=newDescription' | 'test_5679' | 'Common'  | '/myJob/poolMemberCreateOrUpdate' | 'success' | expectedSummaries.notUpdated | expectedSummaries.notUpdated
        TC.C387637 | configurationName | 'ERROR' | '10.200.1.137:80' | 'description=newDescription' | 'test_5679' | 'Common'  | '/myJob/poolMemberCreateOrUpdate' | 'error'   | expectedSummaries.throwError | expectedSummaries.throwError
//  deprecated
//        TC.C387636 | configurationName | '0'     | '10.200.1.137:80' | 'description=newDescription' | 'test_5679' | 'Common'  | '/myJob/poolMemberCreateOrUpdate' | expectedSummaries.notUpdated | expectedSummaries.notUpdated
//        TC.C387637 | configurationName | 'PUT'   | '10.200.1.137:80' | 'description=newDescription' | 'test_5679' | 'Common'  | '/myJob/poolMemberCreateOrUpdate' | expectedSummaries.replaced   | expectedLogs.replaced
//        TC.C387638 | configurationName | 'PATCH' | '10.200.1.137:80' | 'description=newDescription' | 'test_5679' | 'Common'  | '/myJob/poolMemberCreateOrUpdate' | expectedSummaries.updated    | expectedLogs.updated
    }

    @NewFeature(pluginVersion = "3.0.0")
    @Unroll
    def 'CreatePoolMember: Create some pool members Positive #caseId.ids #caseId.description'() {
        testCaseHelper.createNewTestCase(caseId.ids, caseId.description)

        testCaseHelper.testCasePrecondition("Create Balancing Pool with name $poolName")
        bigIpClient.createBalancingPool(partition, poolName)

        def preconditionPoolMember = "10.200.1.111:80"
        def preconditionPool = poolName
        if (caseId.ids == "C387287") {
            testCaseHelper.testCasePrecondition("Create Balancing Pool with name $preconditionPoolMember")
            bigIpClient.createBalancingPool(partition, preconditionPoolMember)
        }

        testCaseHelper.testCasePrecondition("Create pool member with name $preconditionPoolMember in pool $preconditionPool")
        def preconditionParams = [
                config             : configName,
                updateAction       : method,
                name               : preconditionPoolMember,
                optionalParameters : optionalParameters,
                partition          : partition,
                pool_name          : preconditionPool,
                resultPropertySheet: resultPropertySheet,
        ]
        runProcedure(projectName, createPoolMember, preconditionParams)

        given: "Tests parameters for procedure LTM CreatePoolMemberTests"
        def runImageParams = [
                config             : configName,
                updateAction       : method,
                name               : name,
                optionalParameters : optionalParameters,
                partition          : partition,
                pool_name          : poolName,
                resultPropertySheet: resultPropertySheet,
        ]
        testCaseHelper.addStepContent("Run procedure $createPoolMember with parameters:", runImageParams)

        when: "Run procedure"
        def result = runProcedure(projectName, createPoolMember, runImageParams)
        def jobSummary = getJobProperty("/myJob/jobSteps/$createPoolMember/summary", result.jobId)

        def outputParameters = getJobOutputParameters(result.jobId, 1)
        def jobProperties = getJobProperties(result.jobId)

        def poolMemberInfo = bigIpClient.getPoolMemberInfo(poolName, name)
        poolMemberInfo.items[-1].selfLink = poolMemberInfo.items[-1].selfLink.replace(poolName, "~$partition~$poolName")

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
                    .replace('POOL', poolName))

            testCaseHelper.addExpectedResult("Job Properties:")
            testCaseHelper.addExpectedResult("1. $resultPropertySheet: $propertyName")
            new JsonSlurper().parseText(propertyName) == poolMemberInfo.items[-1]

            testCaseHelper.addExpectedResult("Job OutputParameters:")
            testCaseHelper.addExpectedResult("1. poolMemberCreate: ${outputParameters.poolMemberCreateOrUpdate}")
            new JsonSlurper().parseText(outputParameters.poolMemberCreateOrUpdate) == poolMemberInfo.items[-1]

            testCaseHelper.addExpectedResult("BigIp: member pool $name should be created in pool $poolName")

            if (optionalParameters) {
                for (param in optionalParameters.split(";")) {
                    testCaseHelper.addExpectedResult("BigIp: member pool $name should have option ${param.split("=")[0]} with value ${param.split("=")[1]}")
                    poolMemberInfo.items[-1][param.split("=")[0]] == param.split("=")[1]
                }
            }

            testCaseHelper.addExpectedResult("member pool json: ${poolMemberInfo.items[-1]}")
            poolMemberInfo.items[-1]
            poolMemberInfo.items[-1].name == name
            poolMemberInfo.items[-1].partition == partition
        }

        cleanup:
        try {
            bigIpClient.deleteBalancingPool(partition, poolName)
            if (caseId.ids == "C387287"){
                bigIpClient.deleteBalancingPool(partition, preconditionPoolMember)

            }
            bigIpClient.deleteNode(partition, name)
            bigIpClient.deleteNode(partition, preconditionPoolMember)
        } catch (Throwable e) {
            logger.debug(e.message)
        }

        where:
        caseId     | configName        | method | name              | optionalParameters                | poolName    | partition | resultPropertySheet       | expectedSummary            | expectedLog
        TC.C387286 | configurationName | '0'    | '10.200.1.137:80' | ''                                | 'test_5679' | 'Common'  | '/myJob/poolMemberCreate' | expectedSummaries.default  | expectedLogs.default
        TC.C387287 | configurationName | '0'    | '10.200.1.137:80' | ''                                | 'test_5679' | 'Common'  | '/myJob/poolMemberCreate' | expectedSummaries.default  | expectedLogs.default
    }

    @NewFeature(pluginVersion = "3.0.0")
    @Unroll
    def 'CreatePoolMember: Negative #caseId.ids #caseId.description'() {
        testCaseHelper.createNewTestCase(caseId.ids, caseId.description)

        if (createPool) {
            testCaseHelper.testCasePrecondition("Create Balancing Pool with name $poolName")
            bigIpClient.createBalancingPool(partition, poolName)
        }

        given: "Tests parameters for procedure LTM CreatePoolMemberTests"
        def runImageParams = [
                config             : configName,
                updateAction       : method,
                name               : name,
                optionalParameters : optionalParameters,
                partition          : partition,
                pool_name          : poolName,
                resultPropertySheet: resultPropertySheet,
        ]
        testCaseHelper.addStepContent("Run procedure $createPoolMember with parameters:", runImageParams)

        when: "Run procedure"
        def result = runProcedure(projectName, createPoolMember, runImageParams)
        def jobSummary = getJobProperty("/myJob/jobSteps/$createPoolMember/summary", result.jobId)

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
                    .replace('POOL', poolName)
                    .replace('NAME', name))

            if (error){
//                testCaseHelper.addExpectedResult("Output parameter should contains error message: $error")
//                outputParameters.poolMemberCreateOrUpdate == error
                assert outputParameters.poolMemberCreateOrUpdate == null

//                testCaseHelper.addExpectedResult("property $propertyName should contains error message: $error")
//                propertyName == error
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
        cleanup:
        if (createPool) {
            bigIpClient.deleteBalancingPool(partition, poolName)
        }

        where:
        caseId     | configName        | method | name              | optionalParameters | poolName    | partition | resultPropertySheet       | expectedSummary                  | expectedLog                          | error                 | createPool
        TC.C387288 | ''                | '0'    | '10.200.1.137:80' | ''                 | 'test_5679' | 'Common'  | '/myJob/poolMemberCreate' | expectedSummaries.emptyConfig    | expectedSummaries.emptyConfig        | ''                    | false
        TC.C387289 | configurationName | ''     | '10.200.1.137:80' | ''                 | 'test_5679' | 'Common'  | '/myJob/poolMemberCreate' | expectedSummaries.emptyMethod    | expectedSummaries.emptyMethod        | ''                    | false
        TC.C387290 | configurationName | '0'    | '10.200.1.137:80' | ''                 | 'test_5679' | ''        | '/myJob/poolMemberCreate' | expectedSummaries.emptyPartition | expectedSummaries.emptyPartition     | ''                    | false
        TC.C387291 | configurationName | '0'    | '10.200.1.137:80' | ''                 | ''          | 'Common'  | '/myJob/poolMemberCreate' | expectedSummaries.emptyPool      | expectedSummaries.emptyPool          | ''                    | false
        TC.C387292 | configurationName | '0'    | ''                | ''                 | 'test_5679' | 'Common'  | '/myJob/poolMemberCreate' | expectedSummaries.emptyName      | expectedSummaries.emptyName          | ''                    | false
        TC.C387293 | configurationName | '0'    | '10.200.1.137:80' | ''                 | 'test_5679' | 'Common'  | ''                        | expectedSummaries.emptyResult    | expectedSummaries.emptyResult        | ''                    | false
        TC.C387294 | 'wrongConfig'     | '0'    | '10.200.1.137:80' | ''                 | 'test_5679' | 'Common'  | '/myJob/poolMemberCreate' | expectedSummaries.wrongConfig    | expectedSummaries.wrongConfig        | ''                    | false
//  Deprecated
//        TC.C387295 | configurationName | 'wrong'| '10.200.1.137:80' | ''                 | 'test_5679' | 'Common'  | '/myJob/poolMemberCreate' | expectedSummaries.wrongMethod    | expectedSummaries.wrongMethod        | ''                    | false
        TC.C387296 | configurationName | '0'    | '10.200.1.137:80' | ''                 | 'wrong'     | 'Common'  | '/myJob/poolMemberCreate' | expectedSummaries.wrongPool      | expectedSummaries.wrongPool          | outputErrors.pool     | false
        TC.C387297 | configurationName | '0'    | 'wrong'           | ''                 | 'test_5679' | 'Common'  | '/myJob/poolMemberCreate' | expectedSummaries.wrongName      | expectedLogs.wrongName               | outputErrors.name     | false
        TC.C387298 | configurationName | '0'    | '10.200.1.137:80' | ''                 | 'test_5679' | 'wrong'   | '/myJob/poolMemberCreate' | expectedSummaries.wrongPartition | expectedSummaries.wrongPartition     | outputErrors.partition| false
//  Deprecated
//        TC.C387640 | configurationName | '0'    | '10.200.1.137:80' | ''                 | 'test_5679' | 'Common'  | '/myJob/poolMemberCreate' | expectedSummaries.objectNotFound | expectedSummaries.objectNotFound     | outputErrors.notFound | true
//        TC.C387641 | configurationName | 'PUT'  | '10.200.1.137:80' | ''                 | 'test_5679' | 'Common'  | '/myJob/poolMemberCreate' | expectedSummaries.objectNotFound | expectedSummaries.objectNotFound     | outputErrors.notFound | true
//        TC.C387642 | configurationName | 'PATCH'| '10.200.1.137:80' | ''                 | 'test_5679' | 'Common'  | '/myJob/poolMemberCreate' | expectedSummaries.objectNotFound | expectedSummaries.objectNotFound     | outputErrors.notFound | true
    }

}