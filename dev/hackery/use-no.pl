use strict;
use warnings;

BEGIN { ++$INC{'no.pm'} }
sub no::import   { print "using no\n" }
sub no::unimport { print "noing no\n" }

use no;
no no;
