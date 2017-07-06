use strict;
use warnings;

{
package bif;
require Tie::Scalar;
our @ISA = qw(Tie::Scalar);

sub TIESCALAR { bless {assignments => []}, shift }

sub FETCH { shift->{assignments}->[-1] }
sub STORE {
  my ($self, $rhs) = @_;
  push @{$$self{assignments}}, $rhs;
}
}

{
package replaceme;
require Tie::Scalar;
our @ISA = qw(Tie::Scalar);

sub TIESCALAR { bless {}, shift }
sub FETCH { shift->{assignments}->[-1] }
sub STORE { $_[0] = $_[1]; tie $_[1], 'replaceme' }
}

sub foo {
  tie $_[0], 'bif';
}

sub bar:lvalue {
  tie my $x, 'bif';
  \$x;
}

foo my($x, $a);
$x = 10;
$x = 11;
print "$x\n";

my $y = bar;
$$y = 15;
print tied $$y, "\n";

tie my $z, 'replaceme';
my $p1 = tied $z;
$z = 10;
my $p2 = tied $z;
print "$p1 -> $p2\n";
