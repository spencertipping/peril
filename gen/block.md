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

**TODO:** justify the distinction between `block` and `v`; how is this not
analogous to the struct/primitive distinction, which can be resolved more
elegantly through multimethods?

Going back to the murmurhash example:

```
my $murmurhash3_32 = gen {              # peril::gen::block
  args my ($str, $h);                   # $str and $h are peril::gen::v
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

## Side effects and value tracking
Every new value, whether a block or an expression, registers itself with
whichever block is current. For example, here's a simple function:

```
my $plus_one = qe {
  my ($x) = @_;
  print_ "entering the function\n";
  print_ "adding one to $x\n";          # (interesting magic happening here btw)
  $x + 1;
};
```

The block obviously knows that it owns `$x` since it provided the value. But
the first `print_` isn't obviously tied to `$x`, nor is it related to the
block's Perl return value. The block is made aware of it because `print_`
creates a function-call `peril::gen::v` node that immediately adds itself to
whichever block is current -- so we end up with these nodes in this order:

1. `function_call(print_, "entering the function\n")`
2. `str("adding one to ", $x, "\n")`
3. `function_call(print_, str(...))`
4. `operator('+', $x, 1)` -- the return value of the block

At this point we know enough to do some interesting stuff, including accounting
for each node. For example, because a traversal of `operator('+', $x, 1)`
doesn't touch either `print_` node, we know that `print_` is being used as a
side effect. If, on the other hand, we had written something like this:

```
my $plus_one = qe {
  my ($x) = @_;
  $x * 2;                               # side effect (but not really)
  $x + 1;
};
```

`$x * 2` isn't a real side effect if `$x` is a regular number, so the
surrounding block would error out because no sane person would write code like
this (meaning that there's some kind of misunderstanding about what's going
on).

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
my $g = gen { args my $x; $x + 1 };
my $f = gen { args my $x; &$g($x) };    # generates a function call
my $x = &$f(5);                         # compiles $f and $g, returns 6
```

(Note that a generated function call doesn't have to correspond to a function
call in the target language; the above would likely be inlined directly into
its calling context.)

`gen{}` scoping is used to differentiate between these cases for two reasons:

1. Calling a constant or other silently-promotable value would be ambiguous
2. `gen{}`-compiled functions should be able to operate on `gen` nodes

(2) is what makes it possible for compiled code to be self-hosting.
