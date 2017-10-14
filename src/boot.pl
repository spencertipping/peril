#!/usr/bin/env perl
eval($peril::boot = <<'_');
#line 4 "src/boot.pl"
# TODO: license here

use strict;
use warnings;

$peril::commit_id = 0;

package peril::commit
{
  sub new
  {
    my ($class, $time, $delta, @parents) = @_;
    bless { parents => \@parents,
            time    => $time,
            delta   => $delta,
            id      => ++$peril::commit_id }, $class;
  }
}

package peril::image
{
  sub new
  {
    bless { branches => {},
            tags     => {},
            commits  => {} }, shift;
  }
}

exit 0;
_
die $@;
