use strict;
use warnings;

BEGIN { *grep = sub(&@) { print "you've grepped something\n" } }

# aha, grep is too magical to be replaced
my @xs = grep {$_ & 1} 1, 2, 3;
