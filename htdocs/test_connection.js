function get_xhr(){
    var xhr = (window.XMLHttpRequest) ? new XMLHttpRequest() : new activeXObject("Microsoft.XMLHTTP");
    xhr.open( 'post', '/commander/serverRequest.php?xml=1', true );
    xhr.setRequestHeader('Content-Type', 'text/plain;');
    return xhr;
}

function parse_xml(txt){
    var xmlDoc;
    if (window.DOMParser) {
        parser = new DOMParser();
        xmlDoc = parser.parseFromString(txt, "text/xml");
    }
    else {
        xmlDoc = new ActiveXObject("Microsoft.XMLDOM");
        xmlDoc.async = false;
        xmlDoc.loadXML(txt);
    }
    return xmlDoc;
}


function test_connection() {
    var config;
    if (document.getElementsByName("config")[0]){
        config = document.getElementsByName("config")[0].value;
    }
    else{
        let params = (new URL(document.location)).searchParams;
        config = params.get("configName");
    }
    var desc = document.getElementsByName("desc")[0].value;
    var protocol = document.getElementsByName("protocol")[0].value;
    var host = document.getElementsByName("host")[0].value;
    var urlPath = document.getElementsByName("urlPath")[0].value;
    var port = document.getElementsByName("port")[0].value;
    var credential_username = document.getElementsByName("credential_username")[0].value;
    var credential_password = document.getElementsByName("credential_password")[0].value;
    var credential_password2 = document.getElementsByName("credential_password2")[0].value;

    var xhr = get_xhr();

    var string = '<serverRequest><requests><request requestId="1"><runProcedure><projectName>/plugins/@PLUGIN_NAME@/project</projectName><procedureName>TestConnection</procedureName><actualParameter><actualParameterName>config</actualParameterName><value>' + config + '</value></actualParameter><actualParameter><actualParameterName>credential</actualParameterName><value>credential</value></actualParameter><actualParameter><actualParameterName>attemptConnection</actualParameterName><value>1</value></actualParameter><actualParameter><actualParameterName>desc</actualParameterName><value>' + desc + '</value></actualParameter><actualParameter><actualParameterName>host</actualParameterName><value>' + host + '</value></actualParameter><actualParameter><actualParameterName>port</actualParameterName><value>' + port + '</value></actualParameter><actualParameter><actualParameterName>protocol</actualParameterName><value>' + protocol + '</value></actualParameter><actualParameter><actualParameterName>urlPath</actualParameterName><value>' + urlPath + '</value></actualParameter><credential><credentialName>credential</credentialName><userName>' + credential_username + '</userName><password>' + credential_password + '</password></credential></runProcedure></request></requests><requestMap><requests divId="div2"><requestMapping actualId="1" requestedId="2"/></requests></requestMap></serverRequest>';


    xhr.onreadystatechange = function() {
        if (xhr.readyState == XMLHttpRequest.DONE) {
            let txt = xhr.responseText;

            let xmlDoc = parse_xml(txt);

            let jobId = xmlDoc.getElementsByTagName("jobId")[0].childNodes[0].nodeValue;
            check_test_job(jobId);
        }
    }

    
    xhr.send(string);
    document.getElementsByName("Test_Button")[0].value = "Initiating connection...";
}


function show_job_summary(jobId,status){
    var xhr = get_xhr();

    var str ='<serverRequest><requests><request requestId="0"><getJobDetails><jobId>/jobs/' + jobId + '</jobId></getJobDetails></request></requests><requestMap><requests divId="div2"><requestMapping actualId="0" requestedId="1"/></requests></requestMap></serverRequest>';
    xhr.onreadystatechange = function() {
        if (xhr.readyState == XMLHttpRequest.DONE) {
            let txt = xhr.responseText;
            let xmlDoc = parse_xml(txt);

            var property = xmlDoc.getElementsByTagName("property");

            for(var i=0;i<property.length;i++){
                if(property[i].getElementsByTagName("propertyName")[0].childNodes[0].nodeValue == "summary"){
                    let message = "Connection OK";
                    if (status == 'error'){
                        message = "Connection FAILED";
                    }
                    alert(message + "\n" + property[i].getElementsByTagName("value")[0].childNodes[0].nodeValue);
                    break;
                }

            }
        }
    }

    xhr.send(str);
}

function check_test_job(jobId){
    var xhr = get_xhr();

    var str ='<serverRequest><requests><request requestId="0"><getJobStatus><jobId>/jobs/' + jobId + '</jobId></getJobStatus></request></requests><requestMap><requests divId="div2"><requestMapping actualId="0" requestedId="1"/></requests></requestMap></serverRequest>';
    xhr.onreadystatechange = function() {
        if (xhr.readyState == XMLHttpRequest.DONE) {
            let txt = xhr.responseText;

            let xmlDoc = parse_xml(txt);

            let status = xmlDoc.getElementsByTagName("status")[0].childNodes[0].nodeValue;

            if (status != 'completed'){
                setTimeout(function(){ check_test_job(jobId); }, 2000);
                document.getElementsByName("Test_Button")[0].value = "Still working..";
            }
            else{
                let status = xmlDoc.getElementsByTagName("outcome")[0].childNodes[0].nodeValue
                show_job_summary(jobId, status);
                document.getElementsByName("Test_Button")[0].value = "Test Connection";
                document.getElementsByName("Test_Button")[0].disabled = false;
            }
        }
    }

    xhr.send(str);
    document.getElementsByName("Test_Button")[0].value = "Checking connection..";

}
