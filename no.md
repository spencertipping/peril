# `no` language
Peril is written in `no`, which is a self-hosting language that's written in
Perl and can be compiled to anything (sometimes multiple things at once). `no`
is more of an execution fabric than a typical language, and as such it's
relatively conservative in its set of features.

`no` has two fundamental syntactic elements:

1. `no <thing> ...;`: define a thing
2. `nb {...}`: quote a `no` code block

The basic idea is that `no` uses abstract values inside real Perl code and
watches what happens to them in order to reconstruct your functions, sort of
like a higher-level `(quote)` in Lisp.

`no` does things that modify Perl's global state, so it's a scoped abstraction;
you need to `use no` and then `no no` to delimit regions of code where it's
active. Internally, we hijack Perl's `require` by duplicity: we pretend to have
already loaded `no.pm`, which then frees us to do whatever we want to inside
`import` and `unimport`.

```perl
BEGIN { ++$INC{'no.pm'} }
```

## `use no` and `no no`
`no` maintains a list of defined meta-things, for instance `struct`. These
become available once you `use no`; `no` also overloads all constants and
regular expressions.

```perl
sub no::import
{ my $p = caller;
  eval "package $p; use overload qw(constant no::const)"; die $@ if $@;
  *{$p::}{$_} = $no::global{$_} for keys %no::global;
  ++$INC{"$_.pm"} for keys %no::meta }

sub no::unimport
{ my $p = caller;
  eval "package $p; no overload"; die $@ if $@;
  delete *{$p::}{$_} for keys %no::global;
  delete $INC{"$_.pm"} for keys %no::meta }
```

## Structs and compilation
`no` is Perl over symbolic values, each of which itself is a real version of
another symbolic value written in `no`. This is subtle, but it means that `no`
is self-hosting and that's both necessary and awesome. This all ends up being
grounded out in a circular definition of the `no` `struct`, which is then
bootstrapped using a passthrough symbolic value (which is fine since it's a
fixed point). This may make more sense in code.
