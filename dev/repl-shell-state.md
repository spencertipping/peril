# REPLs, shells, and state
I'm writing this up as an answer to questions like, "why is this a shell" and
"shells vs repls, what's the difference."

## Background
Stepping all the way back: your computer, as an object, maintains an opaque
internal state and makes modifications to that state. This makes it a
self-modifying object, much like a Lisp or Smalltalk image. But unlike Lisp and
Smalltalk images, operating systems avoid a few pitfalls that make the image
difficult to manage:

1. State is highly structured and highly accessible: anything that persists is
   stored in the filesystem. Files are just bytes, so they can all be
   inspected/transformed. You can't have a file without a name (not strictly
   true, but close in practice).
2. Processes are isolated into virtual address spaces, and IPC is managed
   through a small set of primitives like shared memory and FIFOs.
3. There are a lot of conventions around access patterns: the filesystem is
   separated into "system stuff" and "user stuff" with various
   commonly-accepted wisdom around "don't touch this stuff except with these
   updating processes."
4. We've built tools like git and docker that make operating system state
   easier to manage and replicate.

And, of course, REPLs like bash, equipped with core utilities, impose minimal
ergonomic overhead: syntax is effectively nonexistent for most commands and
system resources like files are given first-class access with one-character
redirection.

...so. Shells == REPLs and OSs == images. Now let's talk about state.

## State and scope
Programming languages almost universally separate state into stack and heap:
stack is "stuff with an easily-quantified lifetime" and heap is "everything
else." Operating systems have a similar distinction, "stuff in memory" vs
"stuff on disk." And abstractions like the parent-process ID enable programs to
have stack-like nesting.

Continuing with the shell == REPL analogy, programs, like functions, are
encapsulated operations that side-effect against global state or return a value
(stdin/stdout). The big difference is that program state is much more carefully
bounded -- though docker demonstrates that we can still do better.

Daemons are programs that behave more like heap than like stack objects: their
lifetime is indefinite, and their output is not in the form of a return value.
They use their address space to encapsulate some state change they impose on
the OS, whether in the form of files, domain sockets, or network ports. You use
short-lived programs to interact with them (e.g. `ssh` is the interface to
`sshd`).

Who defines a daemon's lifetime? For root/system, it's `init` (or systemd or
upstart or whatever) -- but for normal users that's immutable. Normal users
have no straightforward way to define the lifecycle of a daemon systematically;
daemons are managed by hand. There are, however, some programs that blur the
lines a bit:

1. Terminals and shells, which go away when you're done with them (defined
   lifespan) but whose effect is to open a human-usable resource (something you
   can type into).
2. Things like `tmux`, which maintain automatically-managed shared resources.

Crucially, any object that manages daemons like this needs some way of knowing
when the daemon resource should be terminated -- so you couldn't have a
standalone command-line tool that did a good job maintaining a daemon's
lifetime. Best case it would end up killing the daemon after a timeout, then
restarting the daemon (which is potentially expensive) on demand. Or the daemon
would just run forever.

## So is that why peril is a shell?
Yep, that's the big reason. There are also some other reasons:

1. Peril connects systems together, both locally and over the network; bash
   isn't as well-connected. For example, I can't tab-complete files on other
   systems without writing a custom bash completer, and that ends up having the
   same kinds of daemon-management issues I described above.
2. Shells don't support any serious abstraction ability. I can't configure my
   shell to coherently manage my files in ways analogous to structs/classes in
   programming languages, so encapsulation and polymorphism are off the table.
3. A shell is a translator, but it has only minimal automation abilities. It
   doesn't have a good way to manage lifetimes of things or manipulate objects
   inside other runtimes or machines.

...basically, shells are too literal in their rendition of the world to do what
I'd ultimately like my shell to do. Using bash is sort of like programming in C
-- I'm writing peril to be able to program in Perl.
