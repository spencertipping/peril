use strict;
use warnings;

use overload;

our $qr;

sub foo::int::str    { "int(${+shift})" }
sub foo::float::str  { "float(${+shift})" }
sub foo::binary::str { "binary(${+shift})" }
sub foo::string::str { "string(${+shift})" }
sub foo::qr::str     { "qr(${+shift})" }

BEGIN { overload::constant integer => sub { bless \shift, 'foo::int' },
                           float   => sub { bless \shift, 'foo::float' },
                           binary  => sub { bless \shift, 'foo::binary' },
                           q       => sub { bless \shift, 'foo::string' },
                           qr      => sub { $qr = bless \shift, 'foo::qr' };
        eval qq{package foo::$_; use overload qw/"" str/}
          for qw/int float binary string qr/ }

my $int = 5;
my $float = 4.0;
my $binary = 0x3;
my $q = q{2};
"foo" =~ /1*/;

BEGIN { overload::remove_constant 'q' }

print "$int $float $binary $q $qr\n";
