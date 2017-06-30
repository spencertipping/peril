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
  sig 'PL' => 'L', my ($str, $h);
  for_ unpack_('L*', $str), do_ {
    $_ *= murmur_c1;
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
  sig l => "l", my $x;
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
  sig L => 'L', my ($x);
  if ($x) {           # this line dies because $x belongs to runtime context
    print_ "hi!\n";   # ... (need to use if_ instead)
  }
  if_ $x, do_ {       # this will work
    print_ "hi!\n";
  };
};
```

## `gen{}` blocks
`gen{}` creates a function and specifies how its arguments and return value(s)
should be encoded. It may be translated into another language like C,
Javascript, or Java, and calls to it might be remote -- either to another
process on the same machine or to a process on a different machine. In other
words, `gen{}` fully specifies both language and locality constraints for a
piece of code.

### Signatures
Every `gen{}` block will have a `sig` element that defines input and output
argument encodings in terms of `pack` templates. These templates don't work
quite the way they do in Perl; specifically:

1. Repetition must be length-prefixed and will produce an array _reference_,
   not a list of values. That is, `N/s` will produce a single input argument.
   `s*` isn't allowed.
2. `P` is interpreted as "pass by reference", which implicitly means that the
   caller and callee must share a runtime. The thing you're passing by
   reference doesn't need to be a string; it can be any value. Unlike in Perl,
   `P` becomes a no-op in the compiled code.

`sig` will give you initialized arguments if you specify them after the
arg/return type specs: `sig "ll" => "l", my ($x, $y);` will set `$x` and `$y`
to runtime refs to the input argument values.
