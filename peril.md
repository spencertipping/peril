# Peril
Peril is a distributed shell that can be safely edited while it's running. This
file is the root of its literate source code, which is compiled into Perl at
startup.

You should read these docs if you want to customize peril or understand how it
works under the hood.

## Core stuff: literate source and runtime edits
Most programs conceptually unify their source code and the runtime state that
source produces. Peril is different because it allows you to modify its source
at runtime, and in order to do this safely it needs a way to make sure your
edits haven't damaged its ability to, for example, communicate with other
running instances or save itself to disk. So we need to be more specific than
usual about how state is encoded.

### Images
[Image source](image.md#)

An image is a hashtable that stores Markdown source and other small data. It's
organized sort of like a filesystem in that the keys contain paths; this is
done so that you can write an image as a tarfile and extract it to real files
on disk (this Perl-executable tarfile format is how peril normally persists
itself).

An image can also be compiled into code that can be installed into a Perl
runtime. This happens automatically when you start peril, and you can build
modified images in memory and send them to other Perl interpreters connected
via RPC. If those images fail internal checks or can't communicate over RPC,
peril will refuse to commit the edit into the active runtime.

### Literate compilation
[Literate compiler source](literate.md#)

Peril is written in interlinked Markdown with fenced code segments. The
compiler scans this, searching for links to other Markdown files and for code
regions; it follows the links inline and assembles the code in traversal order.

There are a few things worth mentioning:

1. Fenced regions become method calls against the compiler object, which is
   normally configured to track just `pl` and `perl` and ignore other blocks.
2. Circular links will cause the compiler to fail; if you want to create a link
   cycle that the compiler ignores, end the destination with `#` to make the
   compiler ignore it. (It only follows `.md` links.)
3. The compiler inserts `#line` markers into the compiled source so that any
   errors refer to Markdown locations. This means that the compiled source will
   be slightly longer than the sum of fenced regions.
4. Peril supports some extensions to Markdown; these are rendered as Markdown
   comments, which are named references that go nowhere.
5. Compilers can't `eval` each code block independently; sometimes multiple
   fenced regions will form a more involved syntactic construct, for instance a
   heredoc that is started in one and concluded in another.

### Boot process
There's some custom logic required to unpack the initial peril image and
compile it into the active interpreter; see [boot.md](boot.md#) for details.
