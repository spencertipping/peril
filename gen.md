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
  my ($str, $h) = @_;                   # $str and $h are abstract values
  var uint32 => $h;                     # set type and make $h mutable
  $str->unpack('L*') | qe {
    $_ *= murmur_c1;
    $h ^= ($_ << 15 | $_ >> 17 & 0x7fff) * murmur_c2;
    $h  = ($h << 13 | $h >> 19 & 0x1fff) * 5 + murmur_n;
  };

  my $r = ($str->substr(~3 & $str->length) . "\0\0\0\0")->unpack('V') * murmur_c1;
  $h ^= ($r << 15 | $r >> 17 & 0x7fff) * murmur_c2 ^ $str->length;
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

### How this works
There are a few things going on here:

1. `@_` and `var` return overloaded abstract variables that can generate
   derivatives when you call functions or use operators on them. `var` uses
   `tie()` to track assignments against mutable quantities.
2. `qe{}` uses dynamic scoping track side effect timelines and make sure
   expressions are accounted for. This is how `gen` knows when you've coerced
   an abstract quantity in a concrete position or assigned to an immutable
   quantity, for example.
3. Function names all end in `_` to indicate that we want to _compile_ a call
   to the function rather than execute it immediately. Internally,
   `unpack_(stuff)` is a method call against the current `qe{}` block and will
   be pattern-matched to figure out which alternative is appropriate.

(3) is a lie. Single-dispatch using method calls on structs, then no need for
global functions.

### Using quoted functions
Given the above definition of `$murmurhash3_32`:

```
# no clue whether this is how it should work
my $perl = perl_runtime->new;
my $c    = c99_runtime->new;

$perl->bind(murmurhash3_32 => $murmurhash3_32);
$c->bind(   murmurhash3_32 => $murmurhash3_32);

my $h = &$murmurhash3_32("foo", 0);         # interpret (slow)
my $h = $perl->murmurhash3_32("foo", 0);    # compile to perl (less slow)
my $h = $c->murmurhash3_32("foo", 0);       # compile to c (fast)
```
