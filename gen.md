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

For example, here's part of murmurhash3 (32-bit) in `gen`:

```
use constant murmur_c1 => 0xcc9e2d51;
use constant murmur_c2 => 0x1b873593;
use constant murmur_n  => 0xe6546b64;

my $murmurhash3_32 = gen {
  sig 'L', 0 => 'L/a', 1 => 'L';    # type signature
  my $h = $_[1] || 0;
  for_(unpack_ 'L*', $_[0]) do_ {
    $_ *= murmur_c1;
    $h ^= ($_ << 15 | $_ >> 17 & 0x7fff) * murmur_c2 & 0xffffffff;
    $h  = ($h << 13 | $h >> 19 & 0x1fff) * 5 + murmur_n;
  }
  # footer calculations...
  $h ^ $h >> 16;
};
```
