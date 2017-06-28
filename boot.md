# Image booting
We need to bootstrap the image at this point -- and part of that involves
specifying how the bootstrapping process works. Peril is stored as an
executable perl+tar+HTML file, which is possible because it uses degrees of
freedom in the tar format to interleave tar and other data. Specifically, the
first few bytes look like this:

```
#!/usr/bin/env perl\0
<<'_';
<script type='peril' id='boot'>
```

`tar` will interpret this as a filename and stop at the first null byte; perl
will interpret this as executable code and quote+discard the tar header, and
browsers will interpret everything until `<script>` as body text and quote the
contents of `<script>` verbatim (including null bytes).

Including the initial header, the peril image file looks like this:

```
[tar header 1 including above magic] : 512 bytes
[tar file data 1]                    : n1 * 512 bytes
[tar header 2]                       : 512 bytes
[tar file data 2]                    : n2 * 512 bytes
...
```

The first tarfile entry is reserved for bootstrap code and not unpacked into
the image, so all we need to do is end the header-quoting heredoc and have the
image, compiler, and tarfile decoder before the second file header. The
bootstrap code ends with `__DATA__` so the rest of the archive is quoted and
accessible.

## Boot code
Any code at this point will be dropped in immediately after the `_` used to
close off the `<<'_'` I used to skip the header. For most purposes we can treat
it as the beginning of the perl script, so let's do some of the usual stuff:

```perl
use strict;
use warnings;
use 5.008;
```

Now we need logic to parse and compile everything after `__DATA__`.

There are three ways we can go here, and I'm not sure which one I want yet.

1. We can include the libraries for the image object, basic IO, literate
   compiler, and tarfile decoder, and write a little bit of logic to wrap it
   all together.
2. We can write a custom minimal thing that sets up the objects per spec
   somehow (duplicating logic but not strictly code; smaller result than option
   (1)).
3. We can write the IO/parsing/etc stuff using a codegen pattern and include
   the compiled result, probably the best of both worlds but at the cost of
   some added complexity.
