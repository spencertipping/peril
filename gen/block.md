# `gen` blocks
This is the entry point for `gen{}`, which uses a lot of dynamically-scoped
state under the hood. Here's roughly what's going on.

- [`peril::gen`](../gen.md#) is the base class for AST nodes. Abstract values
  and blocks are both instances of this.
- `peril::gen::block` is a block object, which encodes side-effects and node
  creation ordering. Blocks have defined input/output parameters, though
  sometimes those parameters are inferred.
- [`peril::gen::v`](v.md#) is an object corresponding to an expression that
  will end up producing a runtime value.

Going back to the murmurhash example:

```
my $murmurhash3_32 = gen {              # peril::gen::block
  sig PL => 'L', my ($str, $h);         # $str and $h are peril::gen::v
  for_ unpack_('L*', $str), do_ {       # both happening here
    $_ *= murmur_c1;                    # $_ is a peril::gen::v
    ...
  };
  my $r = unpack_ V =>                  # unpack_ is polymorphic
    substr_($str, ~3 & length_ $str)    # ...as are substr_ and length_
      . "\0\0\0\0";
  ...
  $h ^ $h >> 16;                        # implicit return used as block return
};
```

`gen(&)` creates and ultimately returns a `peril::gen::block` object whose type
signature is `PL => L`. Here are the mechanics:

```perl
package peril;
our @gen_block_scope;

sub gen_current_block
{ die "not inside a gen{} block" unless @gen_block_scope;
  $gen_block_scope[-1] }

sub gen(&)
{ die "cannot gen{} from inside another gen{} context" if @gen_block_scope;
  peril::gen::block->from_fn(shift) }
```

Decorators like `sig` are implemented as method calls against the current
block:

```perl
package peril::gen::block;
sub defdecorator($)
{ no strict 'refs';
  my ($name) = @_;
  die "$name() is valid only inside gen{}" unless @peril::gen_block_scope;
  ${"peril::$name"} = sub { shift->$name(@_) } }

BEGIN { defdecorator 'sig' }
```

## Side effects and value tracking
Every new value, whether a block or an expression, registers itself with
whichever block is current. For example, here's a simple function:

```
my $plus_one = gen {
  sig L => 'L', my $x;
  print_ "entering the function\n";
  print_ "adding one to $x\n";          # (interesting magic happening here btw)
  $x + 1;
};
```

`sig()` grabs a reference to `$x`, which the block will of course know it owns.
But the first `print_` isn't obviously tied to `$x`, nor is it related to the
block's Perl return value. The block is made aware of it because `print_`
creates a function-call `peril::gen::v` node that immediately adds itself to
the block -- the block ends up with these nodes in this order:

1. `$x` (from `sig`)
2. `function_call(print_, "entering the function\n")`
3. `str("adding one to ", $x, "\n")`
4. `function_call(print_, str(...))`
5. `operator('+', $x, 1)` -- the return value of the block

At this point we know enough to do some interesting stuff, including accounting
for each node. For example, because a traversal of `operator('+', $x, 1)`
doesn't touch either `print_` node, we know that `print_` is being used as a
side effect. If, on the other hand, we had written something like this:

```
my $plus_one = gen {
  sig L => 'L', my $x;
  $x * 2;                               # side effect (but not really)
  $x + 1;
};
```

`$x * 2` isn't a real side effect if `$x` is a regular number, so the
surrounding block would error out because no sane person would write code like
this (meaning that there's some kind of misunderstanding about what's going
on).

## Type signatures and metadata
Blocks are self-contained units of code, which means they can be compiled
independently. For example:

```
gen {
  sig P => 'L', my ($x);                # P: $x is a reference
  my $foo = substr_($x, 10);            # ...to a string, apparently
  for_ _(1..10), do_ {                  # do_{} creates a sub-block
    print_ "$_\n";
  };
}
```

The `do_{}` block has an inferred type signature of `("L", "")`: it accepts a
long int and returns nothing. Because it calls `print_`, which is a function
whose side-effect flag is set, the block is also marked as having a side
effect.

Here's where this gets interesting. The block doesn't refer to any `P`-typed
values, so there's no reason it needs to be compiled into the same language as
the surrounding `for_` loop. So from a compilation perspective, `print_` could
be happening in a C subprocess while the surrounding context is fixed by the
`P` type signature of its argument. `print_` could also be happening on an
entirely different machine.

Inferred type signatures reflect any referenced values within a block:

```
gen {
  sig P => 'L', my ($x);                # P: $x is a reference
  for_ _(1..10), do_ {
    print_ "$x\n";                      # now $x is the block arg
  };
}
```

Now the inner block signature becomes `("P", "")` because the block refers to
`$x` -- so we probably can't compile the inner block independently. I say
"probably" because there are two exceptions:

1. If the value for `$x` is typed as something like `L` for which references
   are irrelevant, then the specialized signature will become `("L", "")` and
   the block will become independent.
2. `print_ "$x\n"` doesn't depend on the loop variable, so `print_` is the only
   IO-serializing element involved here. If evaluating `"$x\n"` doesn't have
   side effects or refer to the IO timeline, then it can be moved out of the
   block and passed in as an argument instead; then the block signature would
   specialize to `("La", "")`, which is a quantified dependency and can be
   moved.

## Alternative block syntaxes
Nodes are compile-time objects with methods you can call to write code. For
example, these are all equivalent conditionals:

```
gen {
  if_ $cond, do_ {print_ "hi!\n"};
  if_($cond)->do(print_ "hi!\n");       # NB: no underscore on do()
  if_($cond)->do(sub {print_ "hi!\n"}); # ditto
  print_("hi!\n")->if($cond);           # ditto for if()
}
```

Node methods don't end in `_` because the method call itself happens at
compile-time. These methods happen to behave like macros in that they translate
to declarative control flow modifications, but that isn't always the case; for
example, you could just as easily have compile-time assertions:

```
gen {
  $x->die_if_typed_as('L');             # you'd have to write this method
}
```

## Functions and interop
Blocks and values can be called as functions, which means different things
inside and outside of `gen{}`. Inside `gen{}`, calling a value will generate
and return a `function_call` value node. Outside `gen{}`, calling a value will
compile it and evaluate it on the given set of arguments.

```
my $g = gen { sig L => 'L', my $x; $x + 1 };
my $f = gen {
  sig L => 'L', my $y;
  &$g($y);                              # generates a function call
};
my $x = &$f(5);                         # compiles $f, returns 6
```

(Note that a generated function call doesn't have to correspond to a function
call in the target language; the above would likely be inlined directly into
its calling context.)

`gen{}` scoping is used to differentiate between these cases for two reasons:

1. Calling a constant or other silently-promotable value would be ambiguous
2. `gen{}`-compiled functions should be able to operate on `gen` nodes

(2) is what makes it possible for compiled code to be self-hosting.
