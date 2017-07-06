use strict;
use warnings;

{
package noisy;
require Tie::Scalar;
our @ISA = qw(Tie::Scalar);

sub TIESCALAR { bless \$_[1], $_[0] }
sub FETCH { ${$_[0]} }
sub STORE {
  print "${$_[0]} got assigned $_[1]!\n";
}
}

sub f:lvalue {
  tie my $r, 'noisy', $_[0];
  $r;
}

my $x = 5;
my $y = 6;
f($x + $y) = 10;
