use strict;
use warnings;

package foo;
use overload qw/= clone/;

sub clone   {my ($self) = @_; ++$$self; bless \$$self}
sub DESTROY {my ($self) = @_; --$$self}

package foobar;

my $rc = 0;
{
  my $i1 = bless \$rc, 'foo';
  print "$rc should be 1\n";
  {
    my $i2 = $i1;
    print "$rc should be 2\n";
  }
  print "$rc should be 1\n";
}
print "$rc should be 0\n";
