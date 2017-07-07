use strict;
use warnings;

BEGIN { ++$INC{"foo.pm"} }

package foo;
sub import { my $self = shift; print "using foo @_\n" }

package bar;
use foo qw/bar bif baz/;
use foo 'hi';
use foo 'hi', 'there';
