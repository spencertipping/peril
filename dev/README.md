# Peril design notes
## Language design
Peril is a delicate balance between declarative and procedural, hence the
reliance on OOP. Internally, syntactic elements become objects that are then
_projected_ into a model using methods defined on those objects. The parser
itself can be modified by defining (parser-)combinatory subclasses of
`/peril/parser`. This is how new construction domains are implemented.

With that in mind, peril has a certain type of homoiconicity to it, although
that homoiconicity is lower-level than what you get in something like Lisp
because there's a direct 1:1 correspondence between UTF-8 and parsed input:
peril preserves comments, whitespace, and other things that most parsers would
ignore, which allows it to support lossless source code modification. This type
of runtime revision is supported in part by parse projections that operate in
terms of editor states as well as UTF-8 bytes (where an editor state is
something easier to work with if you're writing an editor, like an array of
lines instead of flat bytes). Similarly, syntax highlighting, high-level editor
manipulation, and other such features are also byproducts of the parser system.

### The purpose of code in this language
Peril is declarative in terms of intent, but declarations modify world state. In
that sense, code is a log of changes you're making to the initial peril image.
It doesn't always make sense to commit to every such change, however; instead,
peril holds these changes as data (your code is quoted) until you commit them to
a branch. You can also evaluate some code within the context of a specific
branch or tag; this helps prevent global-namespace collisions that might cause
problems.

Unlike most languages, peril is designed to behave like a user interface whose
states are easy to serialize. Nearly every nontrivial API defines some custom
syntax and set of editing operations that makes it easier to work with, and a
substantial part of abstraction involves propagating parsers-as-branches to the
right location to enable shorthands where they're needed. Similarly, object
state itself follows commit-style semantics: most parsers provide inverses for
edit deltas that can be applied back to the original source code.

The only degree of mutability in peril is the ability to update a reference;
values themselves are immutable because they can be referred to from multiple
branches. This means that peril is not itself a processing language. Instead, it
gives you the ability to describe computations you want to run. There's a very
deliberate separation between immutable/encodable and mutable/transient values;
this prevents you from introducing dependencies that would compromise the
immutability of values like your code (which is crucial: you want to force a
commit before peril goes and changes things about your source; without this
you'd have no undo feature).

## Hosting model
Like all great languages, peril is self-hosting. It bootstraps itself using a
minimal executive combinator that manipulates the hosting Perl runtime when you
give it source code. This entry point is defined as the `/peril/boot` branch,
whose parse table is configured to read commits in a low-level form and use
them to update `/peril/boot`.

### Execution model
Every value can be used as an input to a parser, which means that parsers
advance the runtime state. This implies a few design choices:

1. Decisions are made by parser alternation.
2. Loops are implemented recursively.
3. Notation == representation; polymorphism is a decision.
4. Functions, methods, and all definitions are modifications to parsers.
5. Optimizations are libraries of deep parse rules.
