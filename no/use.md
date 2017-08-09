# `use no`
We need to tell Perl we've already loaded a file called `no.pm`; this makes
`require no` work, which in turn makes it possible to `use no`.

```perl
package no;
BEGIN { ++$INC{'no.pm'} }
use overload;
```

`no` maintains a list of defined meta-things, for instance `struct`. These
become available once you `use no`; `no` also overloads all constants and
regular expressions.

```perl
sub no::import
{ my $p = caller;
  overload::constant integer => \&no::const::int,
                     float   => \&no::const::float,
                     binary  => \&no::const::binary,
                     q       => \&no::const::q,
                     qr      => \&no::const::qr;
  ${$p::}{$_} = $no::global{$_} for keys %no::global;
  for (keys %no::entry) { ++$INC{"$_.pm"}; ${"${_}::"}{unimport} = $no::entry{$_} } }

sub no::unimport
{ my $p = caller;
  delete ${$p::}{$_} for keys %no::global;
  overload::remove_constant qw/integer 0 float 0 binary 0 q 0 qr 0/;
  for (keys %no::entry) { delete $INC{"$_.pm"}; delete ${"${_}::"}{unimport} } }
```
