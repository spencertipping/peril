use strict;
use warnings;

package foo;
our $x;
sub bar:lvalue { $x }

package main;
(bless {}, 'foo')->bar = 10;
print "$foo::x\n";
