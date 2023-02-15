package EC::BigIp::Handler;

use strict;
use warnings;

use base qw(EC::Plugin::Core);

use Data::Dumper;
use English qw( -no_match_vars );
use JSON;

use EC::BigIp::Client;

#*****************************************************************************
use constant PARAMS_CONFIG_SYNC => qw(
    config
    deviceGroup
    resultPropertySheet
);

use constant BALANCING_POOL_YES => qq/Balancing pool %s~%s has been %s/;
use constant BALANCING_POOL_NO  => qq/Balancing pool %s~%s hasn't been %s/;

use constant PARAMS_CREATE_OR_UPDATE_BALANCING_POOL => qw(
    config
    partition
    name
    updateAction
    optionalParameters
    resultPropertySheet
);

# allowNat
# allowSnat
# appService
# autoscaleGroupId
# description
# gatewayFailsafeDevice
# ignorePersistedWeight
# ipTosToClient
# ipTosToServer
# linkQosToClient
# linkQosToServer
# loadBalancingMode
# minActiveMembers
# minUpMembers
# minUpMembersAction
# minUpMembersChecking
# monitor
# tmPartition
# queueDepthLimit
# queueOnConnectionLimit
# queueTimeLimit
# reselectTries
# serviceDownAction
# slowRampTime
# trafficAccelerationStatus

use constant REQUIRED_CREATE_OR_UPDATE_BALANCING_POOL => qw(
    config
    partition
    name
    updateAction
    resultPropertySheet
);

use constant PARAMS_GET_BALANCING_POOL => qw(
    config
    partition
    name
    resultPropertySheet
);

use constant PARAMS_GET_BALANCING_POOL_LIST => qw(
    config
    resultPropertySheet
);

use constant POOL_MEMBER_YES => qq/Pool member %s~%s~%s has been %s/;
use constant POOL_MEMBER_NO  => qq/Pool member %s~%s~%s hasn't been %s/;

use constant PARAMS_CREATE_OR_UPDATE_POOL_MEMBER => qw(
    config
    partition
    pool_name
    name
    updateAction
    optionalParameters
    resultPropertySheet
);

# address
# appService
# connectionLimit
# description
# dynamicRatio
# ephemeral
# inheritProfile
# logging
# monitor
# priorityGroup
# rateLimit
# ratio
# session
# state
# trafficAccelerationStatus

use constant REQUIRED_CREATE_OR_UPDATE_POOL_MEMBER => qw(
    config
    partition
    pool_name
    name
    updateAction
    resultPropertySheet
);

use constant PARAMS_GET_POOL_MEMBER_LIST => qw(
    config
    partition
    pool_name
    resultPropertySheet
);

use constant PARAMS_GET_POOL_MEMBER => qw(
    config
    partition
    pool_name
    name
    resultPropertySheet
);

use constant PARAMS_CHANGE_POOL_MEMBER_STATUS => qw(
    config
    partition
    pool_name
    name
    set_status
    resultPropertySheet
);

use constant VALUES_CHANGE_POOL_MEMBER_STATUS => {
    enabled => {
        session => 'user-enabled',
        state   => 'user-up',        # unchecked
    },
    disabled => {
        session => 'user-disabled',
        state   => 'user-up',
    },
    force_off => {
        session => 'user-disabled',
        state   => 'user-down',
    },
};

#*****************************************************************************

=head2 after_init_hook

Debug level - we are reading property /projects/EC-PluginName-1.0.0/debugLevel.
If this property exists, it will set the debug level. Otherwize debug level will be 0, which is info.

=cut

#-----------------------------------------------------------------------------
sub after_init_hook {
    my ($self, %params) = @_;

    $self->{plugin_name} = '@PLUGIN_NAME@';
    $self->{plugin_key}  = '@PLUGIN_KEY@';
    my $debug_level = 0;

    if ($self->{plugin_key}) {
        eval {$debug_level = $self->ec()->getProperty("/plugins/$self->{plugin_key}/project/debugLevel")->findvalue('//value')->string_value;};
    }

    if ($debug_level) {
        $self->debug_level($debug_level);
        $self->logger->level($debug_level);
        $self->logger->debug("Debug enabled for $self->{plugin_key}");
    }
    else {
        $self->debug_level(0);
    }

    print "Using plugin $self->{plugin_name}\n";
} ## end sub after_init_hook

#*****************************************************************************

=head2 params

Returns params of the running step.

=cut

#-----------------------------------------------------------------------------
sub params {
    my ($self, $params) = @_;

    if ($params) {
        $self->{params} = $params;
    }

    return $self->{params};
}

#*****************************************************************************
# {
# 'protocol' => 'https',
# 'port' => '8443',
# 'urlPath' => '',
# 'host' => '10.200.1.158',
# 'password' => 'Uazu3ou9',
# 'desc' => '',
# 'attemptConnection' => '1',
# 'userName' => 'admin',
# 'debugLevel' => '2',
# 'credential' => 'bigip_conf'
# }
#-----------------------------------------------------------------------------
sub config {
    my ($self, $name) = @_;

    $name ||= $self->params->{config} || '';
    unless ($self->{configs}->{$name}) {
        $self->{configs}->{$name} = $self->get_config_values($name);
    }

    return $self->{configs}->{$name};
}

#*****************************************************************************

=head2 client

BIG-IP REST Client.

=cut

#-----------------------------------------------------------------------------
sub client {
    my ($self) = @_;

    unless ($self->{client}) {
        my $config = $self->config;

        $self->{client} = EC::BigIp::Client->new({
                %{$config},
                logger => $self->logger,
            }
        );

        unless ($config->{userName}) {
            $self->logger->info("No username is provided in configuration, anonymous access");
        }
    }

    return $self->{client};
}

#*****************************************************************************
sub run_step {
    my ($self, $step_name) = @_;

    $self->SUPER::run_step($step_name);

    $self->client->delete_token;

    return;
}

#*****************************************************************************
sub validate_param_exists {
    my ($self, $param_name) = @_;

    my $param_value = $self->params->{$param_name};
    unless (defined($param_value) && ($param_value ne '')) {
        $self->bail_out(qq{Required parameter "$param_name" is missing});
    }

    return;
}

#*****************************************************************************
sub parse_optional_params {
    my ($self, $params) = @_;

    my $text = delete($params->{optionalParameters}) || '';

    $text =~ s/^\s+//s;
    $text =~ s/\s+$//s;

    if (!$text) {
        return $params;
    }

    my @lines = split(m/\s*[;]+\s*/, $text);
    for my $line (@lines) {
        my ($param, $value) = split(m/\s*=+\s*/, $line, 2);
        if (defined($value) && ($value ne '')) {
            $params->{$param} = $value;
        }
    }

    return $params;
} ## end sub parse_optional_params

#*****************************************************************************
sub process_result {
    my ($self, $title, $output_name, $is_success, $message, $result, $is_json) = @_;

    if ($is_success) {
        if ($is_json) {
            my $params   = $self->params;
            my $property = $params->{resultPropertySheet};

            $self->ec->setProperty($property, $result);
            $self->logger->info(qq{Set property "$property" to value "$result"});

            $self->set_output_parameter($output_name, $result);
        }
        elsif ($result) {
            $message .= "\n\n" . $result;
        }

        $self->logger->info($message);
        $self->success($message);

        my $ec = $self->ec;

        $ec->setProperty("/myCall/summary", $message);
        if ($self->in_pipeline) {
            $ec->setProperty("/myPipelineStageRuntime/ec_summary/$title", $message);
        }
        else {
            $ec->setProperty("/myJobStep/summary", $message);
        }
    } ## end if ($is_success)
    else {
        $self->logger->error($message);
        $self->error($message);

        if ($result =~ m/^\s*</s) {
            $result =~ s/<[^>]+>//sg;
            $result =~ s/(?:\r?\n){2,}/\n\n/sg;
        }

        $self->logger->info("BAILED_OUT:\n$result\n");

        if ($is_json) {
            my $struct = eval {JSON->new->decode($result);};
            if ($struct && $struct->{message}) {
                $message = $struct->{message};
                $message =~ s/^\d+:*\d*:*\s*//;
            }
        }
        else {
            $message = $result;
        }

        $self->finish_procedure_with_error($message);
    } ## end else [ if ($is_success) ]

    return;
} ## end sub process_result

#*****************************************************************************

=head2 checkConnection

Chacks a connection.

=cut

#-----------------------------------------------------------------------------
sub checkConnection {
    my ($self) = @_;

    my ($is_success, $is_json, $result) = $self->client->get_sys_version();

    return ($is_success, $is_json, $result);
}

#*****************************************************************************

=head2 step_config_sync

Synchronizes the local BIG-IP device to the device group.

=cut

#-----------------------------------------------------------------------------
sub step_config_sync {
    my ($self) = @_;

    my $params = $self->get_params_as_hashref(PARAMS_CONFIG_SYNC);

    $self->params($params);

    my $config = $self->config;
    $self->debug_level($config->{debugLevel});

    for my $required (PARAMS_CONFIG_SYNC) {
        $self->validate_param_exists($required);
    }

    my $client = $self->client;

    my $device_group = $params->{deviceGroup};

    my ($is_success, $is_json, $result) = $client->config_sync($device_group);

    my $message;
    if ($is_success) {
        $message = qq{Device group "$device_group" has been synced};
    }
    else {
        $message = qq{Device group "$device_group" hasn't been synced};
    }

    return $self->process_result('LTM - Config sync', 'configSync', $is_success, $message, $result, $is_json);
} ## end sub step_config_sync

#*****************************************************************************

=head2 step_create_or_update_balancing_pool

Create or update balancing pool configuration

=cut

#-----------------------------------------------------------------------------
sub step_create_or_update_balancing_pool {
    my ($self) = @_;

    my $params = $self->parse_optional_params($self->get_params_as_hashref(PARAMS_CREATE_OR_UPDATE_BALANCING_POOL));
    $self->params($params);

    if (!defined $params->{ignorePersistedWeight}) {
        $params->{ignorePersistedWeight} = 'disabled';
    }

    my $config = $self->config;
    $self->debug_level($config->{debugLevel});

    for my $required (REQUIRED_CREATE_OR_UPDATE_BALANCING_POOL) {
        $self->validate_param_exists($required);
    }

    my $data = {};
    for my $field (keys %{$params}) {

        if ($field =~ m/config|updateAction|resultPropertySheet/) {
            next;
        }
        if ($params->{$field} eq '') {
            next;
        }

        $data->{$field} = $params->{$field};
    }

    my $client = $self->client;

    my ($is_success, $is_json, $result) = $client->get_balancing_pool($params);

    my $update_action = $params->{updateAction} || 0;
    my $message;

    if (!$is_success) {
        ($is_success, $is_json, $result) = $client->create_balancing_pool($data);
        $message = sprintf($is_success ? BALANCING_POOL_YES : BALANCING_POOL_NO, $params->{partition}, $params->{name}, 'created');
    }
    elsif (!$update_action) {
        $message = sprintf(BALANCING_POOL_NO, $params->{partition}, $params->{name}, 'touched');
    }
    elsif ($update_action eq 'PUT') {
        ($is_success, $is_json, $result) = $client->manage_balancing_pool($update_action, $data);
        $message = sprintf($is_success ? BALANCING_POOL_YES : BALANCING_POOL_NO, $params->{partition}, $params->{name}, 'replaced');
    }
    elsif ($update_action eq 'PATCH') {
        ($is_success, $is_json, $result) = $client->manage_balancing_pool($update_action, $data);
        $message = sprintf($is_success ? BALANCING_POOL_YES : BALANCING_POOL_NO, $params->{partition}, $params->{name}, 'updated');
    }
    elsif ($update_action eq 'ERROR') {
        $is_success = 0;
        $message = sprintf(BALANCING_POOL_YES, $params->{partition}, $params->{name}, 'found');
    }
    else {
        $self->bail_out(qq{Wrong Update Action value: $update_action});
    }

    return $self->process_result('LTM - Create or update balancing pool', 'poolCreateOrUpdate', $is_success, $message, $result, $is_json);
} ## end sub step_create_or_update_balancing_pool

#*****************************************************************************

=head2 step_get_balancing_pool_list

Get balancing pool list

=cut

#-----------------------------------------------------------------------------
sub step_get_balancing_pool_list {
    my ($self) = @_;

    my $params = $self->get_params_as_hashref(PARAMS_GET_BALANCING_POOL_LIST);

    $self->params($params);

    my $config = $self->config;
    $self->debug_level($config->{debugLevel});

    # all fields required
    for my $required (PARAMS_GET_BALANCING_POOL_LIST) {
        $self->validate_param_exists($required);
    }

    my $client = $self->client;

    my ($is_success, $is_json, $result) = $client->get_balancing_pool_list;

    my $message;
    if ($is_success) {
        $message = qq/Balancing pool list has been gotten/;
    }
    else {
        $message = qq/Balancing pool list hasn't been gotten/;
    }

    return $self->process_result('LTM - Get pool list', 'poolGetList', $is_success, $message, $result, $is_json);
} ## end sub step_get_balancing_pool_list

#*****************************************************************************

=head2 step_get_balancing_pool

Get pool configuration

=cut

#-----------------------------------------------------------------------------
sub step_get_balancing_pool {
    my ($self) = @_;

    my $params = $self->get_params_as_hashref(PARAMS_GET_BALANCING_POOL);

    $self->params($params);

    my $config = $self->config;
    $self->debug_level($config->{debugLevel});

    # all fields required
    for my $required (PARAMS_GET_BALANCING_POOL) {
        $self->validate_param_exists($required);
    }

    my $data = {};
    for my $field (keys %{$params}) {

        if ($field =~ m/config|resultPropertySheet/) {
            next;
        }
        if ($params->{$field} eq '') {
            next;
        }

        $data->{$field} = $params->{$field};
    }

    my $client = $self->client;

    my ($is_success, $is_json, $result) = $client->get_balancing_pool($data);

    my $message = sprintf($is_success ? BALANCING_POOL_YES : BALANCING_POOL_NO, $params->{partition}, $params->{name}, 'gotten');

    return $self->process_result('LTM - Get balancing pool', 'poolGet', $is_success, $message, $result, $is_json);
} ## end sub step_get_balancing_pool

#*****************************************************************************

=head2 step_delete_balancing_pool

Delete balancing pool configuration

=cut

#-----------------------------------------------------------------------------
sub step_delete_balancing_pool {
    my ($self) = @_;

    # yes get & delete have the same parameters
    my $params = $self->get_params_as_hashref(PARAMS_GET_BALANCING_POOL);

    $self->params($params);

    my $config = $self->config;
    $self->debug_level($config->{debugLevel});

    # all fields required
    for my $required (PARAMS_GET_BALANCING_POOL) {
        $self->validate_param_exists($required);
    }

    my $data = {};
    for my $field (keys %{$params}) {

        if ($field =~ m/config|resultPropertySheet/) {
            next;
        }
        if ($params->{$field} eq '') {
            next;
        }

        $data->{$field} = $params->{$field};
    }

    my $client = $self->client;

    my ($is_success, $is_json, $result) = $client->delete_balancing_pool($data);

    my $message = sprintf($is_success ? BALANCING_POOL_YES : BALANCING_POOL_NO, $params->{partition}, $params->{name}, 'deleted');

    if ($is_success) {
        $result ||= '{"code":200,"message":"Success"}';
    }

    return $self->process_result('LTM - Delete balancing pool', 'poolDelete', $is_success, $message, $result, $is_json);
} ## end sub step_delete_balancing_pool

#*****************************************************************************

=head2 step_create_or_update_pool_member

Create or update the set of pool members that are associated with a load balancing pool

=cut

#-----------------------------------------------------------------------------
sub step_create_or_update_pool_member {
    my ($self) = @_;

    my $params = $self->parse_optional_params($self->get_params_as_hashref(PARAMS_CREATE_OR_UPDATE_POOL_MEMBER));
    $self->params($params);

    my $config = $self->config;
    $self->debug_level($config->{debugLevel});

    for my $required (REQUIRED_CREATE_OR_UPDATE_POOL_MEMBER) {
        $self->validate_param_exists($required);
    }

    my $data = {};
    for my $field (keys %{$params}) {

        if ($field =~ m/config|updateAction|resultPropertySheet/) {
            next;
        }
        if ($params->{$field} eq '') {
            next;
        }

        $data->{$field} = $params->{$field};
    }

    my $client = $self->client;

    my ($is_success, $is_json, $result) = $client->get_pool_member($params);

    my $update_action = $params->{updateAction} || 0;
    my $message;

    if (!$is_success) {
        ($is_success, $is_json, $result) = $client->create_pool_member($data);
        $message = sprintf($is_success ? POOL_MEMBER_YES : POOL_MEMBER_NO, $params->{partition}, $params->{pool_name}, $params->{name}, 'created');
    }
    elsif (!$update_action) {
        $message = sprintf(POOL_MEMBER_NO, $params->{partition}, $params->{pool_name}, $params->{name}, 'touched');
    }
    elsif ($update_action eq 'PUT') {
        ($is_success, $is_json, $result) = $client->manage_pool_member($update_action, $data);
        $message = sprintf($is_success ? POOL_MEMBER_YES : POOL_MEMBER_NO, $params->{partition}, $params->{pool_name}, $params->{name}, 'replaced');
    }
    elsif ($update_action eq 'PATCH') {
        ($is_success, $is_json, $result) = $client->manage_pool_member($update_action, $data);
        $message = sprintf($is_success ? POOL_MEMBER_YES : POOL_MEMBER_NO, $params->{partition}, $params->{pool_name}, $params->{name}, 'updated');
    }
    elsif ($update_action eq 'ERROR') {
        $is_success = 0;
        $message = sprintf(POOL_MEMBER_YES, $params->{partition}, $params->{pool_name}, $params->{name}, 'found');
    }
    else {
        $self->bail_out(qq{Wrong Update Action value: $update_action});
    }

    return $self->process_result('LTM - Create or update pool member', 'poolMemberCreateOrUpdate', $is_success, $message, $result, $is_json);
} ## end sub step_create_or_update_pool_member

#*****************************************************************************

=head2 step_change_pool_member_status

Change pool member status

=cut

#-----------------------------------------------------------------------------
sub step_change_pool_member_status {
    my ($self) = @_;

    my $params = $self->parse_optional_params($self->get_params_as_hashref(PARAMS_CHANGE_POOL_MEMBER_STATUS));
    $self->params($params);

    my $config = $self->config;
    $self->debug_level($config->{debugLevel});

    for my $required (PARAMS_CHANGE_POOL_MEMBER_STATUS) {
        $self->validate_param_exists($required);
    }

    my $set_status = $params->{set_status};

    if (!exists VALUES_CHANGE_POOL_MEMBER_STATUS->{$set_status}) {
        $self->bail_out(qq{Wrong value for set_status: $set_status});
    }

    my $data = {%{VALUES_CHANGE_POOL_MEMBER_STATUS->{$set_status}}};
    for my $field (keys %{$params}) {

        if ($field =~ m/config|set_status|resultPropertySheet/) {
            next;
        }
        if ($params->{$field} eq '') {
            next;
        }

        $data->{$field} = $params->{$field};
    }

    my $client = $self->client;

    my ($is_success, $is_json, $result) = $client->change_pool_member_status($data);

    my $message = sprintf($is_success ? POOL_MEMBER_YES : POOL_MEMBER_NO, $params->{partition}, $params->{pool_name}, $params->{name}, 'changed');

    return $self->process_result('LTM - Change pool member status', 'poolMemberStatus', $is_success, $message, $result, $is_json);
} ## end sub step_change_pool_member_status

#*****************************************************************************

=head2 step_get_pool_member_list

Get the set of pool members that are associated with a load balancing pool

=cut

#-----------------------------------------------------------------------------
sub step_get_pool_member_list {
    my ($self) = @_;

    my $params = $self->get_params_as_hashref(PARAMS_GET_POOL_MEMBER_LIST);

    $self->params($params);

    my $config = $self->config;
    $self->debug_level($config->{debugLevel});

    # all fields required
    for my $required (PARAMS_GET_POOL_MEMBER_LIST) {
        $self->validate_param_exists($required);
    }

    my $client = $self->client;

    my ($is_success, $is_json, $result) = $client->get_pool_member_list($params);

    my $message;
    if ($is_success) {
        $message = qq/Pool member list has been gotten/;
    }
    else {
        $message = qq/Pool member list hasn't been gotten/;
    }

    return $self->process_result('LTM - Get member list', 'poolMemberGetList', $is_success, $message, $result, $is_json);
} ## end sub step_get_pool_member_list

#*****************************************************************************

=head2 step_get_pool_member

Get pool member that are associated with a load balancing pool

=cut

#-----------------------------------------------------------------------------
sub step_get_pool_member {
    my ($self) = @_;

    my $params = $self->get_params_as_hashref(PARAMS_GET_POOL_MEMBER);

    $self->params($params);

    my $config = $self->config;
    $self->debug_level($config->{debugLevel});

    # all fields required
    for my $required (PARAMS_GET_POOL_MEMBER) {
        $self->validate_param_exists($required);
    }

    my $data = {};
    for my $field (keys %{$params}) {

        if ($field =~ m/config|resultPropertySheet/) {
            next;
        }
        if ($params->{$field} eq '') {
            next;
        }

        $data->{$field} = $params->{$field};
    }

    my $client = $self->client;

    my ($is_success, $is_json, $result) = $client->get_pool_member($data);

    my $message = sprintf($is_success ? POOL_MEMBER_YES : POOL_MEMBER_NO, $params->{partition}, $params->{pool_name}, $params->{name}, 'gotten');

    return $self->process_result('LTM - Get pool member', 'poolMemberGet', $is_success, $message, $result, $is_json);
} ## end sub step_get_pool_member

#*****************************************************************************

=head2 step_delete_pool_member

Delete pool members that are associated with a load balancing pool

=cut

#-----------------------------------------------------------------------------
sub step_delete_pool_member {
    my ($self) = @_;

    # yes get & delete have the same parameters
    my $params = $self->get_params_as_hashref(PARAMS_GET_POOL_MEMBER);

    $self->params($params);

    my $config = $self->config;
    $self->debug_level($config->{debugLevel});

    # all fields required
    for my $required (PARAMS_GET_POOL_MEMBER) {
        $self->validate_param_exists($required);
    }

    my $data = {};
    for my $field (keys %{$params}) {

        if ($field =~ m/config|resultPropertySheet/) {
            next;
        }
        if ($params->{$field} eq '') {
            next;
        }

        $data->{$field} = $params->{$field};
    }

    my $client = $self->client;

    my ($is_success, $is_json, $result) = $client->delete_pool_member($data);

    my $message = sprintf($is_success ? BALANCING_POOL_YES : BALANCING_POOL_NO, $params->{partition}, $params->{name}, 'deleted');

    if ($is_success) {
        $result ||= '{"code":200,"message":"Success"}';
    }

    return $self->process_result('LTM - Delete pool member', 'poolMemberDelete', $is_success, $message, $result, $is_json);
} ## end sub step_delete_pool_member

#*****************************************************************************
1;
