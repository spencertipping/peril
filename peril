#!/usr/bin/env perl
eval($peril::boot = <<'_');
#line 4 "src/boot.pl"
# TODO: license here

use strict;
use warnings;

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
