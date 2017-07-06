# Generated code
`gen` is a way to write code in Perl that can then be cross-compiled into other
languages for performance reasons. For example, a Perl implementation of
murmurhashing is very slow compared to a native C implementation, and peril
should try to use C if possible. If you write murmurhash using `gen`, peril
will do this automatically.

For example, here's murmurhash3 (32-bit) in `gen`:

```
use peril::gen;
use constant murmur_c1 => 0xcc9e2d51;
use constant murmur_c2 => 0x1b873593;
use constant murmur_n  => 0xe6546b64;

my $murmurhash3_32 = qe {
  args string => my $str,               # string names a struct...
       uint32 => my $h;                 # ...as does uint32
  var $h;                               # this makes $h mutable

  unpack_('L*', $str) | qe {
    $_ *= murmur_c1;
    $h ^= ($_ << 15 | $_ >> 17 & 0x7fff) * murmur_c2;
    $h  = ($h << 13 | $h >> 19 & 0x1fff) * 5 + murmur_n;
  };

  my $r = unpack_(V => substr($str, ~3 & length_ $str) . "\0\0\0\0") * murmur_c1;
  $h ^= ($r << 15 | $r >> 17 & 0x7fff) * murmur_c2 ^ length_ $str;
  $h  = ($h ^ $h >> 16) * 0x85ebca6b;
  $h  = ($h ^ $h >> 13) * 0xc2b2ae35;
  return_ $h ^ $h >> 16;
};
```

## `qe{}` and abstract values
`qe{}` is a mechanism to quote evaluation, similar to the way `qr//` quotes a
regular expression. Perl evaluates the body as a regular function, but `qe{}`
uses operator overloading and `tie()` to build a graph of value operations and
function calls. The result is an abstract function object that can compile your
code using a number of different backends.

## Value types
`gen` views everything in terms of structs.
