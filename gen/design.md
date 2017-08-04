# Design notes
This seems like it would be better suited to some kind of metaclass setup,
particularly if compile-time objects are portable into the runtime. There may
also be opportunities to transcode values across RPC if we do it correctly.

This gets into details about how structs work. Arguably this means we need a
fixed-point definition of structs themselves. We can assume polymorphism is
built into the backend; if we don't, we'll likely fail to leverage it when it
is. (So for languages like C, just compile structs with vtable pointers and
number all methods as a shim.)

In a world where everything has a vtable, how are primitives encoded? Flattened
at runtime maybe -- but capturing value vs reference types isn't completely
trivial. Do we implement nop clone operators for ref types?

- NB: We should also be able to leverage known monomorphism for backends that
  support it.

We need the metaclass layer to be aware of the value/ref distinction -- we'd
need this anyway to handle things like lvalues in general.

Part of the challenge here is working with idiomatic representations of values
in various backends. For example, NumPy provides native matrix values that have
layered/structured access; no sense in trying to down-compile slicing there,
even though in a backend like Javascript it might make sense.

Having polymorphic meta-structures makes sense because we might want to
optimize representations before committing to a compiled form; logical != real
in a number of cases.

## Intermediate representation
I think everything comes down to a series of method calls against abstract
values, some of which have runtime affinity. Values and control flow are still
separately encoded; we need a way to indicate that a side effect should be
kicked.

Basic blocks become their own kinds of special instructions; no variance in
control flow, so they can be optimized individually. Are they objects, or does
this promise more generality than we can realistically offer?

## Proving monomorphism
C++'s mixture of templates and virtuals is useful here. We shouldn't rely on a
proof to prefer `Float32Array` in Javascript; that can be a declared type. So
we might have type inference, but ultimately everything is compiled to be
concrete; _lack of inference does not result in generality, it results in
errors._

Maybe a better way to put it is that the mechanism for specifying inference
steps is orthogonal from the one used for subtyping, as is true in C++.

## What structs are, really
A struct is just (itself) an instance that can tell you (1) how to compile a
method call against it, (2) how to set up a storage location for it, and (3)
how to pack/unpack the value for RMI. It may have other stuff: maybe it also
knows how to make an array type out of itself, etc.

Structs manage virtual-vs-monomorphic invocation of things: any stuff like
bounded types is managed by structs themselves in the compiler backend. (That
is, there isn't necessarily a 1:1 relationship between the types represented by
structs and the types encoded into the compiled code.) There is also no 1:1
mapping between struct instantiations and value instances in the underlying
language: a struct for `{ double x, double y }` could easily be flattened into
two `double` locals if we're compiling to Java, for example.
