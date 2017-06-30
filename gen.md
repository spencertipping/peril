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
  sig 'pL' => 'L', my ($str, $h);
  for_(unpack_ 'L*', $str) do_ {
    $_ *= murmur_c1;
    $h ^= ($_ << 15 | $_ >> 17 & 0x7fff) * murmur_c2 & 0xffffffff;
    $h  = ($h << 13 | $h >> 19 & 0x1fff) * 5 + murmur_n;
  }
  # footer calculations...
  return_ $h ^ $h >> 16;      # as in perl, return_ is optional
};
```

It's worth noting that the above `gen` expression evaluates real Perl stuff;
`for_` is a function and `murmur_c1` etc will be constant-folded away. This
means you can use normal Perl control flow as a macro-level metaprogramming
layer:

```
gen {
  if ($compile_this_way) {
    return_ $_[0] + 1;
  } else {
    return_ $_[0] - 1;
  }
};
```

`gen` will only see one of the two branches depending on the value of
`$compile_this_way`.

## Gen context
The `gen()` function sets up some dynamically-scoped context required for graph
construction. In particular, it manages variables that become node IDs and
implicitly manage sequencing.

```perl
package peril::gen;

# TODO: fix this; we want nestable blocks to manage side-effect tracking
our $gen_id      = 0;     # nonzero if inside a gen{} block
our $next_gen_id = 1;

sub enter_block
{ $gen_id and die "peril::gen: already inside a gen{} block";
  $gen_id = $next_gen_id }

sub exit_block
{ $gen_id or die "peril::gen: not inside a gen{} block (but tried to exit)";
  $next_gen_id = $gen_id;
  $gen_id      = 0 }

sub id
{ $gen_id or die "peril::gen: tried to construct a node outside of a gen{} block";
  $gen_id++ }

sub peril::gen(&)
{ peril::gen::enter_block;
  my $r = &{$_[0]};
  peril::gen::exit_block;
  $r }
```

## Gen values
Most of the notational magic happens inside `peril::gen::v`, which overloads
every operator and builds a reverse-linked graph (that is, `3 + 4` would be
encoded as `"+" -> (3, 4)`, but the `3` and `4` nodes wouldn't link to `+`).

Gen values detect when you're using them in (most) non-gen-aware contexts and
throw errors. This is an easy mistake to make: `sin($gen_value)` is wrong, but
`sin_($gen_value)` works.

```perl
package peril::gen::v;
use overload qw( bool gen_bool    fallback 0
                 0+   gen_number  nomethod gen_op
                 ""   gen_string
                 sin  gen_number  cos   gen_number
                 exp  gen_number  abs   gen_number
                 log  gen_number  int   gen_number
                 sqrt gen_number  atan2 gen_number );

sub id {shift->{id}}

sub gen_bool   { die "peril::gen: cannot coerce abstract value to boolean "
                   . "(this is caused by trying to use a Perl conditional "
                   . "like &&, ||, if, or ?: against a gen node object)" }
sub gen_number { die "peril::gen: cannot coerce abstract value to number" }
sub gen_string { die "peril::gen: cannot coerce abstract value to string" }

sub gen_op
{ my ($self, $rhs, $swap, $op) = @_;
  peril::gen::v->op($op, $swap ? $rhs : $self, $swap ? $self : $rhs) }
```
