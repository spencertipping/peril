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

Now we need logic to parse and compile everything after `__DATA__`. To do that,
let's include some stuff from the standard library:

- [IO stream](io/io.md)
- [Tarfile decoder](io/tar.md)
- [Literate compiler](io/literate.md)
- [Image](image/self.md)

With these things included, the logic here is now quite straightforward. The
only remotely interesting thing happening here is that we need to seek to the
first tarfile entry since we don't know that `__DATA__` immediately precedes
it. (We do, however, know that it's followed by null bytes, which makes this
easy.)

```perl
package peril;
sub bootstrap($)
{ ${my $data_io = peril::io->buffered_fh(shift)->buffer_expand_to(512)} =~ s/^\0+//;
  my $self = peril::image->new;
  my $tar  = $data_io->tar;
  # ??? PROFIT
  }
```
