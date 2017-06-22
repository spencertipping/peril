# Peril image
Peril stores its Markdown source in memory, managing it like a small
filesystem. This makes it possible for you to edit a running instance and
propagate your changes through the RMI fabric. It also makes it possible to
edit Peril within itself, possibly collaboratively.

## Internal storage format
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

## Literate code format
Peril is written in Markdown with fenced Perl snippets. At a high level, here
are the design constraints:

1. We need to start with a series of Markdown files in some vaguely semantic
   layout and end up with a linear stream of source code.
2. Code needs to be inside something that github/gitlab will render correctly,
   in this case fenced blocks. We're at liberty to put arbitrary text after the
   fence's language spec, e.g. to indicate rendering order or snippet names,
   but this won't be displayed by any Markdown renderers.
3. Any shell-language examples should be represented literally so it's easy to
   copy/paste them into a terminal. That is, it's fine for the literate system
   to support templating and nonlinear rendering, but no shell-script example
   commands will be able to use it.
4. We shouldn't require the user to name too many things. It should be easy to
   write a snippet and include it later without having to invent a name for it;
   more specifically, the difficulty should be at most proportional to the
   nonlocality of the reference.

Some of these translate into obvious implementation decisions:

- The compiler follows Markdown links to satisfy (1). A document is included at
  most once, so the first mention dictates the inclusion order. This is the
  only case where inter-file links are allowed; you can't refer to snippets
  outside the current Markdown file.

Others aren't obvious. Getting into those...

### Code block naming
There are couple of ways this could work. One is to name snippets in the
opening fence:

    ```pl snippet-name
    some code
    ```

The other is to mark the code itself when it needs a name, probably using
Perl's `#line` so any backtraces have useful information:

    ```pl
    # line 1 "snippet-name"
    some code
    ```

Of course, the compiler can insert those markers for us, going beyond logical
names and tying them back to the original Markdown file locations.

Given all of this, is there any reason to name snippets at all? Perl is already
fairly flexible about code ordering; maybe we just say that code is rendered as
it's encountered, no indirection necessary or supported. All the compiler will
do is add `#line` markers.
