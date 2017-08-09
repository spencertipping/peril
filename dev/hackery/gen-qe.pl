#!/usr/bin/env perl
use strict;
use warnings;

# Base defs for tracked expressions
package gen::block;
push our @ISA, 'gen';

package gen;
our @blocks;
sub __block() {$blocks[-1]}
sub __enter_block() {push @blocks, bless [], 'gen::block'}
sub __exit_block()  {pop @blocks}

# NB: all methods here need to start with __ to avoid conflicts with quoted
# calls
use overload qw/ fallback 0
                 nomethod __operate
                 ${}      __deref
                 @{}      __array
                 &{}      __compile /;

# NB: nope, don't do this
# meta-types should support methods that generate calls to real code
sub AUTOLOAD
{
}
