package com.electriccloud.plugin.spec

import groovy.json.JsonBuilder
import groovyx.net.http.HTTPBuilder
import groovyx.net.http.Method
import static groovyx.net.http.ContentType.JSON
import static groovyx.net.http.ContentType.TEXT
import static groovyx.net.http.Method.GET
import static groovyx.net.http.Method.POST
import static groovyx.net.http.Method.DELETE

class BigIpClient {

    String protocol
    String host
    String port
    String urlPath
    String endpoint
    String userName
    String password


    private HTTPBuilder http
    private static final Integer SOCKET_TIMEOUT = 20 * 1000
    private static final Integer CONNECTION_TIMEOUT = 5 * 1000

    BigIpClient(String protocol, String host, String port, String urlPath, String userName, String password) {
        def endpoint = "$protocol://$host:$port"
        if (urlPath) {
            endpoint += "/$urlPath"
        }

        this.endpoint = endpoint
        this.protocol = protocol
        this.host     = host
        this.port     = port
        this.urlPath  = urlPath
        this.userName = userName
        this.password = password
        this.http = new HTTPBuilder(endpoint)
        this.http.ignoreSSLIssues()
        this.http.auth.basic( userName, password)
    }

    Object doHttpRequest(Method method, String requestUri,
                         Object requestBody = null,
                         def queryArgs = null) {
        // def requestHeaders = [
        // ]
        http.request(method, JSON) { req ->
            if (requestUri) {
                uri.path = requestUri
            }

            if (queryArgs) {
                uri.query = queryArgs
            }

            // headers = requestHeaders
            body = requestBody
            req.getParams().setParameter("http.connection.timeout", CONNECTION_TIMEOUT)
            req.getParams().setParameter("http.socket.timeout", SOCKET_TIMEOUT)

            response.success = { resp, json ->
                println "[DEBUG] Request for '${requestUri}' was successful ${resp.statusLine}, code: ${resp.status}: $json"
                json
            }

            response.failure = { resp ->
                throw new Exception("[ERROR] Request for '${requestUri}' failed with ${resp.statusLine}, code: ${resp.status}");
            }
        }
    }

    def createDeviceGroup(String partition, String deviceGroup) {
        def deviceGroupData = [
            name        : deviceGroup,
            partition   : partition,
        ]

        def result = doHttpRequest(POST, "/mgmt/tm/cm/device-group/", new JsonBuilder(deviceGroupData).toPrettyString())
        result
    }

    def deleteDeviceGroup(String partition, String deviceGroup) {
        def result = doHttpRequest(DELETE, "/mgmt/tm/cm/device-group/~${partition}~${deviceGroup}")
        result
    }

    def createBalancingPool(String partition, String balancingPool) {
        def balancingPoolData = [
            name        : balancingPool,
            partition   : partition,
        ]
        def result = doHttpRequest(POST, "/mgmt/tm/ltm/pool", new JsonBuilder(balancingPoolData).toPrettyString())
        result
    }

    def getBalancingPoolInfo(def poolName){
        def result = doHttpRequest(GET, "/mgmt/tm/ltm/pool/$poolName/")
        return result
    }

    def getBalancingPoolInfo(def partition,def poolName){
        def result = doHttpRequest(GET, "/mgmt/tm/ltm/pool/~$partition~$poolName/")
        return result
    }

    def deleteBalancingPool(String partition, String balancingPool) {
        def result = doHttpRequest(DELETE, "/mgmt/tm/ltm/pool/~${partition}~${balancingPool}")
        result
    }

    def getPoolMemberInfo(def poolName, def poolMemberName=null){
        def result = doHttpRequest(GET, "/mgmt/tm/ltm/pool/$poolName/members/")
        return result
    }

    def getPools(){
        def result = doHttpRequest(GET, "/mgmt/tm/ltm/pool/")
        return result
    }

    def getPoolMemberInfo(def poolName, def partition, def poolMemberName){
        def result = doHttpRequest(GET, "/mgmt/tm/ltm/pool/$poolName/members/~$partition~$poolMemberName")
        return result
    }

    def deletePoolMember(def partition, def poolName, def name) {
        def result = doHttpRequest(DELETE, "/mgmt/tm/ltm/pool/~$partition~$poolName/members/~$partition~$name")
        result
    }

    def deleteNode(def partition, def name) {
        def result = doHttpRequest(DELETE, "/mgmt/tm/ltm/node/~$partition~${name.split(":")[0]}")
        result
    }

    def createPoolMember(String partition, String poolName, String poolMemberName) {
        def poolMemberData = [
                name        : poolMemberName,
                partition   : partition,
        ]
        def result = doHttpRequest(POST, "/mgmt/tm/ltm/pool/$poolName/members/", new JsonBuilder(poolMemberData).toPrettyString())
        result
    }
}
