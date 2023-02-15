import groovy.transform.BaseScript
import com.electriccloud.commander.dsl.util.BasePlugin

//noinspection GroovyUnusedAssignment
@BaseScript BasePlugin baseScript

// Variables available for use in DSL code
def pluginName = args.pluginName
def upgradeAction = args.upgradeAction
def otherPluginName = args.otherPluginName

def pluginKey = getProject("/plugins/$pluginName/project").pluginKey
def pluginDir = getProperty("/projects/$pluginName/pluginDir").value

//List of procedure steps to which the plugin configuration credentials need to be attached
// ** steps with attached credentials
def stepsWithAttachedCredentials = [
    [procedureName: 'LTM - Config sync', stepName: 'LTM - ConfigSync'],

    [procedureName: 'LTM - Create or update balancing pool', stepName: 'LTM - CreateOrUpdatePool'],
    [procedureName: 'LTM - Get pool list', stepName: 'LTM - GetPoolList'],
    [procedureName: 'LTM - Get balancing pool', stepName: 'LTM - GetPool'],
    [procedureName: 'LTM - Delete balancing pool', stepName: 'LTM - DeletePool'],

    [procedureName: 'LTM - Create or update pool member', stepName: 'LTM - CreateOrUpdatePoolMember'],
    [procedureName: 'LTM - Change pool member status', stepName: 'LTM - ChangePoolMembersStatus'],
    [procedureName: 'LTM - Get member list', stepName: 'LTM - GetMemberList'],
    [procedureName: 'LTM - Get pool member', stepName: 'LTM - GetPoolMember'],
    [procedureName: 'LTM - Delete pool member', stepName: 'LTM - DeletePoolMember']
]
// ** end steps with attached credentials

project pluginName, {

	loadPluginProperties(pluginDir, pluginName)
	loadProcedures(pluginDir, pluginKey, pluginName, stepsWithAttachedCredentials)
	
    //plugin configuration metadata
    property 'ec_formXmlCompliant', value: 'true'
    property 'ec_icon', value: 'images/icon-plugin.svg'
    property 'ec_configurations', value: 'ec_plugin_cfgs'

	property 'ec_config', {
	configLocation = 'ec_plugin_cfgs'
	form = '$[' + "/projects/${pluginName}/procedures/CreateConfiguration/ec_parameterForm]"
		property 'fields', {
			property 'desc', {
				property 'label', value: 'Description'
				property 'order', value: '1'
			}
		}
	}

}

// Copy existing plugin configurations from the previous
// version to this version. At the same time, also attach
// the credentials to the required plugin procedure steps.
upgrade(upgradeAction, pluginName, otherPluginName, stepsWithAttachedCredentials)
