use strict;
use warnings;

our $x = 10;
sub AUTOLOAD:lvalue { $x }

sub foo:lvalue;

foo = 4;
print "$x\n";
