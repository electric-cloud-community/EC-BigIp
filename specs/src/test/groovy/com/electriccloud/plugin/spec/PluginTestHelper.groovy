package com.electriccloud.plugin.spec

import com.electriccloud.spec.*
import spock.lang.Shared

class PluginTestHelper extends PluginSpockTestSupport {

    static def automationTestsContextRun = System.getenv('AUTOMATION_TESTS_CONTEXT_RUN') ?: 'Sanity,Regression,E2E'
    static def PLUGIN_NAME = System.getenv('PLUGIN_NAME') ?: 'EC-BigIp'

    static String BIGIP_USERNAME = getBigIpUsername()
    static String BIGIP_PASSWORD = getBigIpPassword()
    static String BIGIP_AUTHENTICATION_TYPE = getBigIpAuthenticationType()
    static String BIGIP_AUTHENTICATION_PROVIDER = getBigIpAuthenticationProvider()
    static String BIGIP_DEBUG_LEVEL = getBigIpDebugLevel()

    static String BIGIP_LOCAL_PROTOCOL = getBigIpLocalProtocol()
    static String BIGIP_LOCAL_HOST = getBigIpLocalHost()
    static String BIGIP_LOCAL_PORT = getBigIpLocalPort()
    static String BIGIP_LOCAL_URL_PATH = getBigIpLocalUrlPath()

    static String BIGIP_RADIUS_PROTOCOL = getBigIpRadiusProtocol()
    static String BIGIP_RADIUS_HOST = getBigIpRadiusHost()
    static String BIGIP_RADIUS_PORT = getBigIpRadiusPort()
    static String BIGIP_RADIUS_URL_PATH = getBigIpRadiusUrlPath()

    static String bigIpProtocol = (BIGIP_AUTHENTICATION_PROVIDER == "tmos") ? BIGIP_RADIUS_PROTOCOL : BIGIP_LOCAL_PROTOCOL
    static String bigIpHost = (BIGIP_AUTHENTICATION_PROVIDER == "tmos") ? BIGIP_RADIUS_HOST : BIGIP_LOCAL_HOST
    static String bigIpPort = (BIGIP_AUTHENTICATION_PROVIDER == "tmos") ? BIGIP_RADIUS_PORT : BIGIP_LOCAL_PORT
    // TODO: change to 1 after testConnection will be fixed
    static String BIGIP_ATTEMPT_CONNECTION = '1'

    static def commanderServer = System.getenv('COMMANDER_SERVER') ?: 'electricflow'

    static String CONFIG_NAME = 'specConfig'

    @Shared
    def configSyncParams = [
        config             : "",
        deviceGroup        : "",
        resultPropertySheet: ""
    ],  createBalancingPoolParams = [
        config             : CONFIG_NAME,
        updateAction       : '0',
        name               : 'testPoolName',
        optionalParameters : '',
        partition          : 'Common',
        resultPropertySheet: '/myJob/poolCreate'
    ],  getBalancingPoolParams = [
        config             : CONFIG_NAME,
        name               : 'testPoolName',
        partition          : 'Common',
        resultPropertySheet: '/myJob/poolGet'
    ],  getPoolListParams = [
        config             : CONFIG_NAME,
        resultPropertySheet: '/myJob/poolGetList',
    ],  manageBalancingPoolParams = [
        config             : CONFIG_NAME,
        create             : '0',
        method             : 'PATCH',
        name               : 'testPoolName',
        optionalParameters : "allowNat=no;allowSnat=no;ignorePersistedWeight=enabled",
        partition          : 'Common',
        resultPropertySheet: '/myJob/poolManage'
    ],  deleteBalancingPoolParams = [
        config             : CONFIG_NAME,
        name               : 'testPoolName',
        partition          : 'Common',
        resultPropertySheet: '/myJob/poolDelete'
    ],  deletePoolMemberParams = [
        config             : CONFIG_NAME,
        name               : '1.1.1.1:80',
        partition          : 'Common',
        pool_name          : 'testPoolName',
        resultPropertySheet: '/myJob/poolMemberDelete'
    ]


    static def changePoolMemberStatus = 'LTM - Change pool member status'
    static def createPoolMember = 'LTM - Create or update pool member'
    static def getMemberList = 'LTM - Get member list'
    static def getPoolMember = 'LTM - Get pool member'
    static def managePoolMember = 'LTM - Manage pool member'
    static def createPoolMemberParams = [
        config             : "",
        updateAction       : "",
        name               : "",
        optionalParameters : "",
        partition          : "",
        pool_name          : "",
        resultPropertySheet: "",
    ]
    static def changePoolMemberStatusParams = [
        config             : "",
        name               : "",
        partition          : "",
        pool_name          : "",
        resultPropertySheet: "",
        set_status         : ""
    ]
    static def getMemberListParams = [
        config             : "",
        partition          : "",
        pool_name          : "",
        resultPropertySheet: "",
    ]
    static def getPoolMemberParams = [
        config             : "",
        name               : "",
        partition          : "",
        pool_name          : "",
        resultPropertySheet: "",
    ]
    static def managePoolMemberParams = [
        config             : "",
        method             : "",
        name               : "",
        optionalParameters : "",
        partition          : "",
        pool_name          : "",
        resultPropertySheet: "",
    ]

    def createConfiguration(String configName = CONFIG_NAME, Map props = [:], Map params = [:]) {

        if (System.getenv('RECREATE_CONFIG')) {
            props.recreate = true
        }

        def authenticationProvider = params.authenticationProvider ?: BIGIP_AUTHENTICATION_PROVIDER

        createPluginConfiguration(
            PLUGIN_NAME,
            configName,
            [
                desc                  : params.desc ?: 'Spec tests configuration',
                protocol              : params.protocol ?: (authenticationProvider == "tmos") ? BIGIP_RADIUS_PROTOCOL : BIGIP_LOCAL_PROTOCOL,
                host                  : params.host ?: (authenticationProvider == "tmos") ? BIGIP_RADIUS_HOST : BIGIP_LOCAL_HOST,
                port                  : params.port ?: (authenticationProvider == "tmos") ? BIGIP_RADIUS_PORT : BIGIP_LOCAL_PORT,
                urlPath               : params.urlPath ?: (authenticationProvider == "tmos") ? BIGIP_RADIUS_URL_PATH : BIGIP_LOCAL_URL_PATH,
                credential            : params.credential ?: 'credential',
                authenticationType    : params.authenticationType ?: BIGIP_AUTHENTICATION_TYPE,
                authenticationProvider: authenticationProvider,
                attemptConnection     : params.attemptConnection ?: BIGIP_ATTEMPT_CONNECTION,
                debugLevel            : params.debugLevel ?: BIGIP_DEBUG_LEVEL,
            ],
            BIGIP_USERNAME,
            BIGIP_PASSWORD,
            props
        )
    }

    BigIpClient getBigIpHelper() {
        return new BigIpClient(
            (BIGIP_AUTHENTICATION_PROVIDER == "tmos") ? BIGIP_RADIUS_PROTOCOL : BIGIP_LOCAL_PROTOCOL,
            (BIGIP_AUTHENTICATION_PROVIDER == "tmos") ? BIGIP_RADIUS_HOST : BIGIP_LOCAL_HOST,
            (BIGIP_AUTHENTICATION_PROVIDER == "tmos") ? BIGIP_RADIUS_PORT : BIGIP_LOCAL_PORT,
            (BIGIP_AUTHENTICATION_PROVIDER == "tmos") ? BIGIP_RADIUS_URL_PATH : BIGIP_LOCAL_URL_PATH,
            BIGIP_USERNAME,
            BIGIP_PASSWORD
        )
    }

    static String getAssertedEnvVariable(String varName) {
        String varValue = System.getenv(varName)
        assert varValue
        return varValue
    }

    static String getBigIpUsername() { getAssertedEnvVariable("BIGIP_USERNAME") }

    static String getBigIpPassword() { getAssertedEnvVariable("BIGIP_PASSWORD") }

    static String getBigIpAuthenticationType() { System.getenv("BIGIP_AUTHENTICATION_TYPE") ?: 'basic' }

    static String getBigIpAuthenticationProvider() { System.getenv("BIGIP_AUTHENTICATION_PROVIDER") ?: 'tmos' }

    static String getBigIpDebugLevel() { System.getenv("BIGIP_DEBUG_LEVEL") ?: '2' }

    static String getBigIpLocalProtocol() { getAssertedEnvVariable("BIGIP_LOCAL_PROTOCOL") }

    static String getBigIpLocalHost() { getAssertedEnvVariable("BIGIP_LOCAL_HOST") }

    static String getBigIpLocalPort() { getAssertedEnvVariable("BIGIP_LOCAL_PORT") }

    static String getBigIpLocalUrlPath() { System.getenv("BIGIP_LOCAL_URL_PATH") ?: '' }

    static String getBigIpRadiusProtocol() { getAssertedEnvVariable("BIGIP_RADIUS_PROTOCOL") }

    static String getBigIpRadiusHost() { getAssertedEnvVariable("BIGIP_RADIUS_HOST") }

    static String getBigIpRadiusPort() { getAssertedEnvVariable("BIGIP_RADIUS_PORT") }

    static String getBigIpRadiusUrlPath() { System.getenv("BIGIP_RADIUS_URL_PATH") ?: '' }
}
