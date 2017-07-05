# `gen` structs
All `gen` values are assumed to be generic, both in terms of representation and
implementation. This includes primitives, which means that RTTI is present iff
the context is polymorphic. Some baseline rules about how this works:

1. Inheritance and type-compability don't exist. Things are either polymorphic
   (type-tagged) or monomorphic (unwrapped, primitive).
2. A struct can hold a reference to a value, but that value can't be shared
   with anyone. If the struct is moved, that value will be copied. Data doesn't
   have identity -- if you need identity for something, you need to get a
   mutable container from the runtime.
3. All functions and operators are fully generic; `substr_` can be specialized
   to work on numbers if you want to write such an implementation, and `+` can
   be specialized on lists. Functions force value types only inasmuch as
   implementations exist or don't exist.
4. Generic containers and type parameters don't exist; you get this
   functionality by having a function that generates new structs.
