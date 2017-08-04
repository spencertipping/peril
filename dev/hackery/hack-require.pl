use strict;
use warnings;

BEGIN { ++$INC{"foo.pm"} }

package foo;
sub import   { my $self = shift; print "using foo @_\n" }
sub unimport { my $self = shift; print "no-ing foo @_\n" }

package bar;
use foo qw/bar bif baz/;
use foo 'hi';
use foo 'hi', 'there';

sub qe(&) {shift}
use foo qe{print};
no foo qe{print};

BEGIN {
  if (0) {
    *require = sub {print "would have required $_[0]\n"};
    require foo::bar;   # NB: this fails; can't replace the implementation of require()
  }
}

BEGIN { ++$INC{'no.pm'} }

sub no::unimport { $_[1]->() }

sub n(&) {shift}
no no n{print "hi\n"};

# NB: this fails because foo::bar isn't in %INC; no() calls require() just like
# use() does
sub foo::bar::unimport { print "unimport foo::bar @_\n" }
no foo::bar qw/1 2 3/;
