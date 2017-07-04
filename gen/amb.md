# `gen` ambivalence
`gen{}` code lets you specify degrees of freedom within your code: that is,
alternatives that are assumed to be functionally equivalent. This is how
language-specific implementations of native functions are specified, for
example.

**TODO:** what exactly does this look like? It needs to support adding a new
language at runtime, so existing ambs should be extensible. What are the
tradeoffs of making this a general "do either" construct?
