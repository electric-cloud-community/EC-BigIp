$[/myProject/scripts/preamble_pl]

use strict;
use warnings;

use JSON;
use Data::Dumper;

use ElectricCommander;

use EC::BigIp::Handler;

#*****************************************************************************
use constant {
    SUCCESS => 0,
    ERROR   => 1,
};

use constant {
    LEVEL_ERROR => -1,
    LEVEL_INFO  => 0,
    LEVEL_DEBUG => 1,
    LEVEL_TRACE => 2,
};

#*****************************************************************************
my $ec = ElectricCommander->new;
# $ec->abortOnError(0);

my $projName   = '$[/myProject/projectName]';
my $pluginName = '@PLUGIN_NAME@';
my $pluginKey  = '@PLUGIN_KEY@';

my $debugLevel = '$[debugLevel]';

my $hander = EC::BigIp::Handler->new(
    project_name => $projName,
    plugin_name  => $pluginName,
    plugin_key   => $pluginKey
);

$hander->logger->level($debugLevel);
$hander->debug_level($debugLevel + 1);

# local $Data::Dumper::Indent   = 2;
# local $Data::Dumper::Sortkeys = 1;
# local $Data::Dumper::Deepcopy = 1;

#*****************************************************************************
sub prepare {
    my $params = {};

    ## load option list from procedure parameters
    my $xpath   = $ec->getJobDetails($ENV{COMMANDER_JOBID});
    my $nodeset = $xpath->find('//actualParameter');
    foreach my $node ($nodeset->get_nodelist) {
        my $parm = $node->findvalue('actualParameterName');
        my $val  = $node->findvalue('value');
        $params->{$parm} = "$val";
    }

    my $cred = $ec->getFullCredential($params->{credential});

    $params->{userName} = $cred->findvalue("//userName")->string_value;
    $params->{password} = $cred->findvalue("//password")->string_value;

    # $hander->logger->debug(Dumper(['#001', $params]));

    return $params;
} ## end sub prepare

#*****************************************************************************
sub checkConnection {
    my $params = prepare();

    $hander->{configs}->{''} = $params;
    $hander->params({});

    my ($is_success, $is_json, $result) = $hander->checkConnection();

    # $hander->logger->debug(Dumper(['#002', $is_success, $is_json, $result]));

    if ($is_success) {
        local $@ = undef;
        my $data = eval {decode_json($result)};
        my $eval_error = $@;

        if (!$data || $eval_error) {
            return (1, '', '', $eval_error);
        }

        my $right_key = '';
        foreach my $key (keys %{$data->{entries}}) {
            $right_key = $key if $key =~ /\/0$/;
        }

        my $entries = $data->{entries}->{$right_key}->{nestedStats}->{entries};

        my $version = $entries->{Version}->{description};
        if (!$version) {
            return (1, '', '', 'Something wrong with API response');
        }

        my $product = $entries->{Product}->{description} || 'BIG-IP';

        return (0, sprintf('F5 %s version %s', $product, $version), '', '');
    } ## end if ($is_success)

    my $errmsg;
    if ($is_json) {
        local $@ = undef;
        my $data = eval {JSON->new->decode($result);};
        my $eval_error = $@;

        if (!$data || $eval_error) {
            return (1, '', '', $eval_error);
        }

        if ($data->{message}) {
            $errmsg = $data->{message};
            $errmsg =~ s/^\d+:*\d*:*\s*//;

            if ($data->{code}) {
                $errmsg = $data->{code} . ': ' . $errmsg;
            }
        }
    }
    else {
        ($errmsg) = $result =~ m#<title>([^.]+)</title>#s
    }

    $errmsg ||= $result;

    return (1, '', '', $errmsg);
} ## end sub checkConnection

#*****************************************************************************
my ($code, $stdout, $stderr, $errmsg) = eval {checkConnection();};
my $evalError = $@;

if ($evalError) {
    if ($errmsg) {
        $errmsg .= "\n$evalError";
    }
    else {
        $errmsg = $evalError;
    }

    $code ||= 1;
}

$hander->out(LEVEL_INFO, 'STDOUT: ', $stdout) if ($stdout);
$hander->out(LEVEL_INFO, 'STDERR: ', $stderr) if ($stderr);
$hander->out(LEVEL_INFO, 'ERRMSG: ', $errmsg) if ($errmsg);
$hander->out(LEVEL_INFO, 'EXIT_CODE: ', $code);

if ($code) {
    my $suggestions = q{Reasons could be due to one or more of the following. Please ensure they are correct and try again.:
1. Are your 'Protocol', 'Host', 'URL Path to API' and 'API server port' correct?
2. Is your 'Type Of Authentication' correct?
3. Credentials - Are your credentials correct?
   Are you able to use these credentials to work with BigIp using 'curl', 'wget', etc.?
};

    $errmsg ||= $stderr || $stdout;

    $hander->configurationErrorWithSuggestions($errmsg, $suggestions);

    exit(ERROR);
}
else {
    $hander->logger->info("Connection succeeded");

    exit(SUCCESS);
}
