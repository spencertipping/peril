# Peril image
Peril stores its Markdown source in memory, managing it like a small
filesystem. This makes it possible for you to edit a running instance and
propagate your changes through the RMI fabric. It also makes it possible to
edit Peril within itself, possibly collaboratively.

Structurally speaking, an image is an in-memory snapshot of Peril's internal
filesystem that may or may not have been committed to a perl runtime. The
bootstrap process loads the "self" image from a tarfile and commits it, but you
can maintain multiple images in memory and use different formats to encode
them; most remotes, for instance, use precompiled code so we don't have to ship
the Markdown docs.

```pl
package peril::image;
```

## State
An image is defined by the following attributes:

- `%fs`: a mapping from logical filename (e.g. `/peril/image.p.md`) to the
  verbatim contents of the file
- `$root`: the filename of the literate source file to start with when
  compiling (the main image uses `/peril/peril.p.md`)

Nothing about an image's existence implies that it's been loaded into a perl
runtime; that happens automatically when you _boot_ one, but not when you're
working with one in memory.

## Serialization
### Full archive
Because Peril behaves like a data container, its format is designed to maximize
accessibility: you can run it with `perl`, you can extract its contents using
`tar`, or you can open it with a browser to inspect its contents. This is
possible because of two degrees of freedom:

1. `tar` magic is stored at byte offset 257; both POSIX tar and ustar formats
   begin with a filename field, which can contain 100 bytes of arbitrary data.
2. Web pages can start with body text and tags will still work; as a result, we
   can drop a `<script type='peril'>` early in the file and the browser will
   store+quote data from that point forwards (until it encounters `</script>`).
   Chrome seems to have no problems with null bytes in the webpage.

Note that you can't use `tar` to repack a valid Peril image; no archiver will
produce something that will be executable as perl code unless you do something
elaborate like this:

```sh
$ mkdir -p $'#!/usr/bin/env perl\nprint "hi!\n";\n__END__\n'
$ tar -c \#\!/*/*/* | perl
hi!
```

Peril authors a tarfile that `tar` itself would never create (involving some
null-byte hackery) to minimize the gnarliness of the initial artifact
directory and guarantee correct handling of some internal fields.

See the [image generator](image/generator.p.md) for details.

## Literate code format
Peril is written in Markdown with fenced Perl snippets. There are two rules
that govern how Markdown is translated into executable code:

1. Markdown links function as inclusion (with an implied include-at-most-once
   rule).
2. Fenced code is rendered as it's encountered if it's in the language we're
   trying to compile; otherwise it's ignored. For Perl, the compiler adds
   `#line` markers so backtraces refer to Markdown source locations.

Details and interop are discussed in the generator source linked above.
