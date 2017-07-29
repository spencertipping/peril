use strict;
use warnings;

package foo;
use overload qw/+ plus/;
sub new {my $x = 0; bless \$x, shift}
sub plus:lvalue {${+shift}}

package bar;
sub f:lvalue {shift}
my $x = foo->new;
f($x + 0) = 5;                # can't assign into bare + due to syntax rules
print "$$x\n";
