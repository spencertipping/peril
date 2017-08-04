#!/usr/bin/env perl
use strict;
use warnings;

# Base defs for tracked expressions
package gen::block;
push our @ISA, 'gen';

package gen;
our @blocks;
sub _block() {$blocks[-1]}
sub _enter_block() {push @blocks, bless [], 'gen::block'}
sub _exit_block()  {pop @blocks}

# NB: all methods here need to start with _ to avoid conflicts with quoted
# calls
use overload qw/ fallback 0
                 nomethod _operate
                 ${}      _deref
                 @{}      _array
                 &{}      _compile /;
