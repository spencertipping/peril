# Types in `peril::gen`
We've got an abstract evaluator, so we've basically got a factoring of values
into compile-time `(known, unknown)` pairs -- i.e. types. Optimization involves
minimizing the amount of stuff we send to the `unknown` side.

In principle, there's no reason functions need to be written using concrete
type values; they can all be written over generic parameters and complain when
we instantiate them against incompatible arguments. For example:

```
my $fn = qe { shift + 1 };
&$fn([1, 2, 3]);                        # error: don't have + for arrays maybe
```

In other words, proof of executability is done at call-time; then we resolve
everything since the types are fully specified.

## What's wrong with this approach
Runtime polymorphism is useful. For example, it's possible to specialize a
single invocation to a list of `int, string, string, boolean` or something, but
there's no sense in forcing a recompilation for every set of list types.

So we have tagged unions. **BUT:** if the idea is to compile these into real
`union{}` types in C, we'll want to set boundaries on the set of possibilities
so we don't have runaway sizes. This means we've got runtime polymorphism
bounded by compile-time stuff, so two options:

1. Have some kind of verification (and most likely implicit conversions) to
   make sure bounds are valid.
2. Fail catastrophically at runtime if they aren't.

I prefer (2): types should be more "take my word for it" than "I'm a glutton
for fascism."
