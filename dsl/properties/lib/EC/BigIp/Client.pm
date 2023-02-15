package EC::BigIp::Client;

use strict;
use warnings;

use LWP::UserAgent;
use LWP::Protocol::https;
use JSON;
use Encode qw(encode);
use URI;
use HTTP::Status qw(HTTP_UNAUTHORIZED);
use Data::Dumper;

#*****************************************************************************
# local $Data::Dumper::Indent   = 2;
# local $Data::Dumper::Sortkeys = 1;
# local $Data::Dumper::Deepcopy = 1;
#*****************************************************************************
sub new {
    my ($class, $params) = @_;

    my $uri = URI->new;
    $uri->scheme($params->{protocol});
    $uri->host($params->{host});
    $uri->port($params->{port});

    my $url_path = $params->{urlPath};
    if (defined($url_path)) {
        $url_path =~ s/^\s+//gs;
        $url_path =~ s/\s+$//gs;
        if ($url_path eq '/') {
            $url_path = '';
        }
    }

    my $self = {
        url                     => $uri->as_string,
        url_path                => $url_path,
        authenticationType      => $params->{authenticationType} || 'basic',
        username                => $params->{userName},
        password                => $params->{password},
        authentication_provider => $params->{authenticationProvider} || 'tmos',
        logger                  => $params->{logger},
        proxy                   => $params->{proxy},
        token                   => '',
    };

    return bless $self, $class;
} ## end sub new

#*****************************************************************************
sub logger {shift->{logger}}

#*****************************************************************************

=head2 json

Returns JSON object.

=cut

#-----------------------------------------------------------------------------
sub json {
    my ($self) = @_;

    if (!$self->{json}) {
        $self->{json} = JSON->new->utf8;
    }

    return $self->{json};
}

#***********************************************************************
sub ua {
    my ($self) = @_;

    unless ($self->{ua}) {
        local $ENV{PERL_NET_HTTPS_SSL_SOCKET_CLASS} = "Net::SSL";
        local $ENV{PERL_LWP_SSL_VERIFY_HOSTNAME}    = 0;            # avoid certificate verification
        local $ENV{HTTPS_DEBUG}                     = 0;

        $self->{ua} = LWP::UserAgent->new(
            ssl_opts => {
                verify_hostname => 0,
                SSL_verify_mode => 0x00,
            },
        );
    }

    return $self->{ua};
}

#*****************************************************************************
sub is_response_json {
    my ($self, $response) = @_;

    return ($response->header('Content-Type') =~ m#^application/json#) ? 1 : 0;
}

#*****************************************************************************
sub _login_tba {
    my ($self, $options) = @_;

    if (!$self->{token}) {
        # new login
        my $params = {
            method => 'POST',
            path   => '/mgmt/shared/authn/login',
            params => {
                username          => $self->{username},
                password          => $self->{password},
                loginProviderName => $self->{authentication_provider},
            },
            options => {
                skip_auth => 1,    # avoid the deep recursion
            },
        };

        my ($is_success, $is_response_json, $content) = $self->request($params);
        if (!$is_success || !$is_response_json) {
            return (0, $is_response_json, $content);
        }

        local $@ = undef;
        my $data       = eval {$self->json->decode($content)};
        my $eval_error = $@;

        if (!$data || $eval_error) {
            return (0, 0, $eval_error);
        }

        $self->{token} = $data->{token}->{token};
        if (!$self->{token}) {
            my $errmsg;
            if ($data->{message}) {
                $errmsg = $data->{message};
                $errmsg =~ s/^\d+:*\d*:*\s*//;

                if ($data->{code}) {
                    $errmsg = $data->{code} . ': ' . $errmsg;
                }
            }

            $errmsg ||= "Unexpected API response: $content";

            return (0, 0, $errmsg);
        }
    } ## end if (!$self->{token})

    $options->{headers}->{'X-F5-Auth-Token'} = $self->{token};

    return (1, 0, 'OK');
} ## end sub _login_tba

#*****************************************************************************
sub request {
    my ($self, $args) = @_;

    my ($method, $path, $params, $payload, $options) = @{$args}{qw(method path params payload options)};

    my $uri = URI->new($self->{url});
    $uri->path($self->{url_path} . $path);

    if ($params && ('GET' eq $method)) {
        $uri->query_form(%{$params});
    }

    my $request = HTTP::Request->new($method => $uri);

    if (!$options->{skip_auth}) {
        if ($self->{authenticationType} eq 'tba') {
            my ($is_success, $is_response_json, $content) = $self->_login_tba($options);
            if (!$is_success) {
                return ($is_success, $is_response_json, $content);
            }
        }
        else {
            if ($self->{username} && $self->{password}) {
                $request->authorization_basic(
                    encode('utf8', $self->{username}),
                    encode('utf8', $self->{password})
                );
            }
        }
    }
    elsif (($self->{authenticationType} eq 'tba') && $self->{token}) {
        $options->{headers}->{'X-F5-Auth-Token'} = $self->{token};
    }

    if ($method =~ m/POST|PUT|PATCH|DELETE|OPTIONS/si) {
        if ($payload) {
            $request->content($payload);
        }

        $request->header('Content-Type' => 'application/json');
        if ($params) {
            $request->content($self->json->encode($params));
        }
    }

    if ($options->{headers}) {
        for my $name (keys %{$options->{headers}}) {
            $request->header($name => $options->{headers}->{$name});
        }
    }

    $self->logger->trace($request->as_string);

    local $@ = undef;
    my $response   = eval {$self->ua->request($request);};
    my $eval_error = $@;

    if (!$response || $eval_error) {
        return (0, 0, $eval_error);
    }

    $self->logger->trace($response->as_string);

    my $is_success       = $response->is_success;
    my $is_response_json = $self->is_response_json($response);
    my $content          = $response->content;

    if (!$is_success && !$content) {
        $is_response_json = 0;
        $content          = $response->status_line;
    }

    return ($is_success, $is_response_json, $content);
} ## end sub request

#*****************************************************************************
# https://devcentral.f5.com/s/articles/iControl-REST-Authentication-Token-Management
#-----------------------------------------------------------------------------
sub delete_token {
    my ($self) = @_;

    if ($self->{authenticationType} ne 'tba') {
        return 1;
    }

    if (!$self->{token}) {
        return 0;
    }

    my ($is_success, $is_response_json, $content) = $self->request({
            method  => 'DELETE',
            path    => '/mgmt/shared/authz/tokens/' . $self->{token},
            options => {
                skip_auth => 1,
            },
        }
    );

    # $self->logger->info("Delete token: ".(defined($content) ? $content : ''));

    $self->{token} = '';

    return $is_success ? 1 : 0;
} ## end sub delete_token

#*****************************************************************************
sub call {
    my ($self, $args) = @_;

    my ($is_success, $is_response_json, $content) = $self->request($args);

    if ($is_success || !$is_response_json || (!$is_success && ($self->{authenticationType} ne 'tba'))) {
        return ($is_success, $is_response_json, $content);
    }

    local $@ = undef;
    my $data       = eval {$self->json->decode($content)};
    my $eval_error = $@;

    if (!$data || $eval_error) {
        return (0, 0, $eval_error);
    }

    if (!$data->{message} || !$data->{code}) {
        return ($is_success, $is_response_json, $content);
    }

    if (($data->{message} !~ m/X-F5-Auth-Token does not exist/i) || (HTTP_UNAUTHORIZED != $data->{code})) {
        return ($is_success, $is_response_json, $content);
    }

    # current token has expired
    $self->{token} = '';

    return $self->request($args);
} ## end sub call

#*****************************************************************************
sub get_sys_version {
    my ($self) = @_;

    return $self->call({
            method => 'GET',
            path   => '/mgmt/tm/sys/version',
        }
    );
}

#*****************************************************************************
sub config_sync {
    my ($self, $group_name) = @_;

    my $params = {
        command     => 'run',
        utilCmdArgs => sprintf('config-sync to-group %s', $group_name),
    };

    return $self->call({
            method => 'POST',
            path   => '/mgmt/tm/cm',
            params => $params,
        }
    );
}

#*****************************************************************************
sub create_device_group {
    my ($self, $data) = @_;

    return $self->call({
            method => 'POST',
            path   => '/mgmt/tm/cm/device-group/',
            params => $data,
        }
    );
}

#*****************************************************************************
sub delete_device_group {
    my ($self, $params) = @_;

    return $self->call({
            method => 'DELETE',
            path   => sprintf('/mgmt/tm/cm/device-group/~%s~%s', @{$params}{qw(partition device_group)}),
        }
    );
}

#*****************************************************************************
sub create_balancing_pool {
    my ($self, $data) = @_;

    return $self->call({
            method => 'POST',
            path   => '/mgmt/tm/ltm/pool',
            params => $data,
        }
    );
}

#*****************************************************************************
sub manage_balancing_pool {
    my ($self, $method, $data) = @_;

    return $self->call({
            method => $method,
            path   => sprintf('/mgmt/tm/ltm/pool/~%s~%s', @{$data}{qw(partition name)}),
            params => $data,
        }
    );
}

#*****************************************************************************
sub get_balancing_pool_list {
    my ($self) = @_;

    return $self->call({
            method => 'GET',
            path   => sprintf('/mgmt/tm/ltm/pool'),
        }
    );
}

#*****************************************************************************
sub get_balancing_pool {
    my ($self, $params) = @_;

    return $self->call({
            method => 'GET',
            path   => sprintf('/mgmt/tm/ltm/pool/~%s~%s', @{$params}{qw(partition name)}),
        }
    );
}

#*****************************************************************************
sub delete_balancing_pool {
    my ($self, $params) = @_;

    return $self->call({
            method => 'DELETE',
            path   => sprintf('/mgmt/tm/ltm/pool/~%s~%s', @{$params}{qw(partition name)}),
        }
    );
}

#*****************************************************************************
sub create_pool_member {
    my ($self, $data) = @_;

    return $self->call({
            method => 'POST',
            path   => sprintf('/mgmt/tm/ltm/pool/~%s~%s/members', @{$data}{qw(partition pool_name)}),
            params => $data,
        }
    );
}

#*****************************************************************************
sub manage_pool_member {
    my ($self, $method, $data) = @_;

    return $self->call({
            method => $method,
            path   => sprintf('/mgmt/tm/ltm/pool/~%s~%s/members/~%s~%s', @{$data}{qw(partition pool_name partition name)}),
            params => $data,
        }
    );
}

#*****************************************************************************
sub change_pool_member_status {
    my ($self, $data) = @_;

    return $self->call({
            method => 'PATCH',
            path   => sprintf('/mgmt/tm/ltm/pool/~%s~%s/members/~%s~%s', @{$data}{qw(partition pool_name partition name)}),
            params => $data,
        }
    );
}

#*****************************************************************************
sub get_pool_member_list {
    my ($self, $params) = @_;

    return $self->call({
            method => 'GET',
            path   => sprintf('/mgmt/tm/ltm/pool/~%s~%s/members', @{$params}{qw(partition pool_name)}),
        }
    );
}

#*****************************************************************************
sub get_pool_member {
    my ($self, $params) = @_;

    return $self->call({
            method => 'GET',
            path   => sprintf('/mgmt/tm/ltm/pool/~%s~%s/members/~%s~%s', @{$params}{qw(partition pool_name partition name name)}),
        }
    );
}

#*****************************************************************************
sub delete_pool_member {
    my ($self, $params) = @_;

    return $self->call({
            method => 'DELETE',
            path   => sprintf('/mgmt/tm/ltm/pool/~%s~%s/members/~%s~%s', @{$params}{qw(partition pool_name partition name)}),
        }
    );
}

#*****************************************************************************
1;
