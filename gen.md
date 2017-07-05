# Generated code
`gen` is a way to write code in Perl that can then be cross-compiled into other
languages for performance reasons. For example, a Perl implementation of
murmurhashing is very slow compared to a native C implementation, and peril
should try to use C if possible. If you write murmurhash using `gen`, peril
will do this automatically.

`gen` code looks a lot like Perl code, but has a few subtle differences:

1. Operators and keywords end in `_`; for example, `if_` instead of `if`, and
   blocks are marked with `do_` (otherwise it's a syntax error in perl)
2. Arrays and hashes must be used as scalar refs, not as flat values
3. Values tend to be type-hinted with `pack` templates
4. Things involving regexes tend not to work; if `$x` is a runtime value, you
   can't say `$x =~ s/foo/bar/g` for example.

For example, here's murmurhash3 (32-bit) in `gen`:

```
use constant murmur_c1 => 0xcc9e2d51;
use constant murmur_c2 => 0x1b873593;
use constant murmur_n  => 0xe6546b64;

my $murmurhash3_32 = gen {
  my ($str, $h) = @_;
  for_ unpack_('L*', $str), do_ {
    $_ *= murmur_c1;

    # TODO: we can't track these assignments, which throws a bit of a wrench
    # into this stuff
    $h ^= ($_ << 15 | $_ >> 17 & 0x7fff) * murmur_c2 & 0xffffffff;
    $h  = ($h << 13 | $h >> 19 & 0x1fff) * 5 + murmur_n;
  };

  my $r = unpack_ 'V', substr_($str, ~3 & length_ $str) . "\0\0\0\0";
  $r *= murmur_c1;
  $h ^= ($r << 15 | $r >> 17 & 0x7fff) * murmur_c2 & 0xffffffff ^ length_ $str;
  $h &= 0xffffffff;
  $h  = ($h ^ $h >> 16) * 0x85ebca6b & 0xffffffff;
  $h  = ($h ^ $h >> 13) * 0xc2b2ae35 & 0xffffffff;
  return_ $h ^ $h >> 16;      # as in perl, return_ is optional
};
```

It's worth noting that the above `gen` expression evaluates real Perl stuff;
`for_` is a function and `murmur_c1` etc will be constant-folded away. This
means you can use normal Perl control flow as a macro-level metaprogramming
layer:

```
gen {
  my $x = shift;
  if ($compile_this_way) {
    return_ $x + 1;
  } else {
    return_ $x - 1;
  }
};
```

`gen` will only see one of the two branches depending on the value of
`$compile_this_way`. `gen` will figure out if you use a runtime quantity with a
real Perl conditional and will complain accordingly, for example:

```
gen {
  my ($x) = @_;
  if ($x) {           # this line dies because $x belongs to runtime context
    print_ "hi!\n";   # ... (need to use if_ instead)
  }
  if_ $x, do_ {       # this will work
    print_ "hi!\n";
  };
};
```
