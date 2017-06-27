#line 14 "image/bootstrap.p.md"
use strict;
use warnings;
use 5.008;
#line 15 "image/literate.p.md"
package peril::literate;
sub resolve_path($) { my $p = shift; 1 while $p =~ s|[^/]+/\.\.||; $p }
sub literate_elements_markdown($$)
{ my ($file, @lines) = ($_[0], "", split /\n/, $_[1]);
  my @toggles        = (0, grep $lines[$_] =~ /^\s*\`\`\`/, 1..$#lines);
  (map((join("\n", @lines[$toggles[$_-2]..$toggles[$_-1]]) =~ /\]\(([^)]+\.p\.md)\)/g,
        [$toggles[$_-1] + 1,
         $lines[$toggles[$_-1]] =~ /^\s*\`\`\`(.*)$/,
         join "\n", @lines[$toggles[$_-1]+1..$toggles[$_]-1]]),
       grep !($_ & 1), 1..$#toggles),
   join("\n", @lines[$toggles[-1]..$#lines]) =~ /\]\(([^)]+\.p\.md)\)/g) }
#line 34 "image/bootstrap.p.md"
package peril::image;
sub new { bless {source => {}, parsed => {}, unresolved_links => {}}, shift }
#line 44 "image/bootstrap.p.md"
package peril::boot_fns;
sub compile_literate_source($\%);
sub compile_literate_source($\%)
{ my ($k, $parsed) = @_;
  local $_; map ref() ? $$_[1] =~ /^pl/ ? "#line $$_[0] \"$k\"\n$$_[2]\n" : ()
                      : compile_literate_source($_, %$parsed), @{$$parsed{$k}} }
#line 61 "image/bootstrap.p.md"
sub literate_parse($$\%)
{ my ($boot, $key, $unresolved_links) = @_;
  (my $path = $key) =~ s|[^/]*$||;
  $$boot{parsed}{$key}
    = [my @p = map ref() ? $_ : peril::literate::resolve_path "$path$_",
               peril::literate::literate_elements_markdown $key => $$boot{source}{$key}];
  ++$$unresolved_links{$_} for grep !ref() && !exists $$boot{parsed}{$_}, @p;
  delete $$unresolved_links{$key};
  keys %$unresolved_links
     ? undef
     : join '', compile_literate_source 'peril.p.md', %{$$boot{parsed}} }

sub bootstrap()
{ my $image  = peril::image->new;
  my $header = '';
  1 while read DATA, $header, 512 - length $header, length $header;
  $header =~ s/^\0*([^\0])/$1/;

  while (1)
  { 1 while read DATA, $header, 512 - length $header, length $header;
    my ($name, $length) = unpack 'Z100 x24 a12', $header;
    $length = oct $length;
    my $source = '';
    1 while read DATA, $source, $length - length $source, length $source;

    if ($image->add($name, $source)->is_complete) {
      $image->eval($header)->main(@ARGV);
    }

    # Parse the new entry, compile the full image if we have enough 
    if (defined($header = literate_parse $image, $name)) {
    }

    # Read+discard the rest of the 512-byte block
    $header = ''; 1 while read DATA, $header, (-$length & 511) - length $header;
    $header = '' } }
bootstrap;
__DATA__
peril.md                                                                                            0000644 0001750 0001750 00000004473 13124565675 015407  0                                                                                                    ustar   spencertipping                  spencertipping                                                                                                                                                                                                         # Peril
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
Peril is written in interlinked Markdown with fenced code segments. The
compiler scans this, searching for links to other Markdown files and for code
regions; it follows the links inline and assembles the code in traversal order.

There are a few things worth mentioning:

1. **TODO:** fenced blocks -> method calls against the compiler?
2. Circular links will cause the compiler to fail; if you want to create a link
   cycle that the compiler ignores, end the destination with `#` to make the
   compiler ignore it. (It only follows `.md` links.)
3. The compiler inserts `#line` markers into the compiled source so that any
   errors refer to Markdown locations. This means that the compiled source will
   be slightly longer than the sum of fenced regions.
4. Peril supports some extensions to Markdown; these are rendered as Markdown
   comments, which are named references that go nowhere.
                                                                                                                                                                                                     image.md                                                                                            0000644 0001750 0001750 00000006003 13124500207 015321  0                                                                                                    ustar   spencertipping                  spencertipping                                                                                                                                                                                                         # Peril image
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
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             image/                                                                                              0000755 0001750 0001750 00000000000 13124227426 015011  5                                                                                                    ustar   spencertipping                  spencertipping                                                                                                                                                                                                         image/literate.p.md                                                                                 0000644 0001750 0001750 00000002114 13124214677 017404  0                                                                                                    ustar   spencertipping                  spencertipping                                                                                                                                                                                                         # Literate compiler
Takes a Markdown document (as a string) and a filename, and returns a list of
items describing code and links that were encountered. Each item is one of the
following:

- `"link/destination.p.md"`
- `[line_number, "language", "code"]`

Indented blocks of code are not parsed; this function just finds fenced blocks.

Also note that the link parser only returns links that point to things with a
`.p.md` suffix, and doesn't parse parentheses within the link ref.

```pl
package peril::literate;
sub resolve_path($) { my $p = shift; 1 while $p =~ s|[^/]+/\.\.||; $p }
sub literate_elements_markdown($$)
{ my ($file, @lines) = ($_[0], "", split /\n/, $_[1]);
  my @toggles        = (0, grep $lines[$_] =~ /^\s*\`\`\`/, 1..$#lines);
  (map((join("\n", @lines[$toggles[$_-2]..$toggles[$_-1]]) =~ /\]\(([^)]+\.p\.md)\)/g,
        [$toggles[$_-1] + 1,
         $lines[$toggles[$_-1]] =~ /^\s*\`\`\`(.*)$/,
         join "\n", @lines[$toggles[$_-1]+1..$toggles[$_]-1]]),
       grep !($_ & 1), 1..$#toggles),
   join("\n", @lines[$toggles[-1]..$#lines]) =~ /\]\(([^)]+\.p\.md)\)/g) }
```
                                                                                                                                                                                                                                                                                                                                                                                                                                                    image/generator.p.md                                                                                0000644 0001750 0001750 00000010344 13123750511 017554  0                                                                                                    ustar   spencertipping                  spencertipping                                                                                                                                                                                                         # Image generator
Structurally, the image is a tar file with a mangled entry prepended to it.
(All of this is possible only because `tar` has such an arcane file format; in
this case it's awesome.) This mangled entry contains the shebang line and HTML
`<script>` opener, plus some escaping to set Perl up read the remainder of the
stream as a proper tar file. Here's how this works.

## Tarfile structure
Tarfiles are sequences of `file_header file_data` pairs. A `file_header` has
this structure, which gets padded out to a 512-byte boundary and followed by
literal file data, which is also padded to 512:

```
filename : 0+100
filemode : 100+8
uid      : 108+8
gid      : 116+8
...
type     : 156+1
link     : 157+100
magic    : 257+6        # always "ustar\0"
version  : 263+2        # always "00"
...
```

I'm using the `filename` field to store the following prefix:

```
#!/usr/bin/env perl\0
<<'_';
<script type="peril">
```

## Generating a Peril image
Now we've got enough background to do this properly. First things first:

```pl
package peril::image_fns;
use constant tar_header_pack =>
  q{ a100 a8 a8 a8 a12 a12 a8 c a100 a6 a2 a32 a32 a8 a8 a155 };

sub tar_footer() {"\0" x 1024}

sub octify {sprintf "\%0$_[1]o", $_[0]}
sub tar_header
{ my ($filename, $mode, $uid, $gid, $size, $mtime, # checksum (generated)
      $ftype, $linkname, $uname, $gname, $major, $minor, $prefix) = @_;
  $_ = octify $_, 7  for $mode, $uid, $gid, $major, $minor;
  $_ = octify $_, 11 for $size, $mtime;
  my $checksum_template = pack tar_header_pack,
    my @fs =
      ($filename, $mode, $uid, $gid, $size, $mtime, "        ", $ftype,
       $linkname, $uname, $gname, $major, $minor, $prefix);
  $fs[6] = octify unpack("%24C*", $checksum_template), 6;
  pack "a512", pack tar_header_pack, @fs }

sub tar_padding($) {"\0" x (-$_[0] & 511)}
sub tar_encode_regular_file($$)
{ tar_header($_[0], 0644, $<, $(, length $_[1], time, 0, "", "", "", 0, 0, "")
  . $_[1] . tar_padding length $_[1] }
```

### Bootstrap support code
This isn't entirely straightforward for a few reasons:

1. We have to use few abstractions here, since those should be modifiable down
   the line without changing the bootstrap logic.
2. It should be possible to include arbitrary data in the tarstream; i.e. peril
   can store not only its source, but data closures and other stuff that we
   don't want to store in memory.
3. It should be possible to include arbitrary-length streaming data beyond the
   end of the image tarstream, which is useful if we're using SSH as a
   transport layer for data.

Here's how this works. The initial tarfile entry is a file whose name serves as
the script's shebang line + `<script type='peril'>` tag. The header has number
of fields we ignore by quoting them inside `<<'_'`; then we terminate the
heredoc and jump into the bootstrap logic.

The bootstrap logic is written into the file-contents part of the tar entry and
includes a `__DATA__` footer to quote the rest of Peril's source.

```pl
package peril::image_fns;

use constant tar_header_code =>
  "#!/usr/bin/env perl\0\n<<'_';\n<script type='peril' id='boot'>";

sub tar_encode_header($)
{ tar_header(tar_header_code, 0644, $<, $(, length $_[0], 0, 0, "", "", "",
             0, 0, "peril_artifact_delete_this")
  . $_[0] . tar_padding length $_[0] }

sub tar_encode_bootstrap() {tar_encode_header <<'boot_'}

_
```

The bootstrap code itself has its own module in
[bootstrap.p.md](bootstrap.p.md).

```pl
bootstrap;
__DATA__
boot_
```

### Literate -> image
Peril is built from literate source during the main build process, so let's
implement the logic for that.

```pl
package peril::image_fns;

sub key_from_file($\%)
{ my ($f, $sources) = @_;
  open my $fh, "<$f" or die "key_from_file failed to read $f: $!";
  $$sources{$f} = join '', <$fh> }

sub literate_source_keys($\%);
sub literate_source_keys($\%)
{ my ($key, $sources) = @_;
  (my $path = $key) =~ s|[^/]*$||;
  ($key,
   map !ref()
     ? literate_source_keys(peril::boot::resolve_path "$path$_", %$sources)
     : (), peril::boot::literate_elements_markdown $key, $$sources{$key}) }

sub encode_image($\%)
{ my ($root, $sources) = @_;
  join '', tar_encode_bootstrap,
           map tar_encode_regular_file($_ => $$sources{$_}),
               literate_source_keys $root, %$sources }
```
                                                                                                                                                                                                                                                                                            image/bootstrap.p.md                                                                                0000644 0001750 0001750 00000006507 13124227072 017613  0                                                                                                    ustar   spencertipping                  spencertipping                                                                                                                                                                                                         # Archive bootstrapping
We can make a few simplifying assumptions:

1. All Peril code is encoded before anything else.
2. Peril code is all stored as regular files with tar attributes we can mostly
   ignore.
3. All bytes after `__DATA__\n` are null until the next tarfile entry.
4. The root Markdown file is encoded first.

(4) is required because otherwise the dependency graph could be completed
early.

```pl
use strict;
use warnings;
use 5.008;
```

The bootstrap logic depends on the [literate Markdown compiler](literate.p.md).

## Boot-related image state
We'll define more about images later on, but initially we need to maintain
enough state to incrementally load literate files and construct the Perl
source. This involves three hashes:

- `%source`: maps filenames to the original markdown contents
- `%parsed`: maps filenames to the output of `literate_markdown_elements`
- `%unresolved_links`: a set of filenames that have been linked to but haven't
  been seen yet (we don't have a full image until this set is empty)

This state can be modified and extended at runtime.

```pl
package peril::image;
sub new { bless {source => {}, parsed => {}, unresolved_links => {}}, shift }
```

## Literate compiler/dependency graph
We parse incrementally as we decode stuff, tracking unresolved dependencies as
we go. Once we have a full graph we join the code into a big string and
evaluate it.

```pl
package peril::boot_fns;
sub compile_literate_source($\%);
sub compile_literate_source($\%)
{ my ($k, $parsed) = @_;
  local $_; map ref() ? $$_[1] =~ /^pl/ ? "#line $$_[0] \"$k\"\n$$_[2]\n" : ()
                      : compile_literate_source($_, %$parsed), @{$$parsed{$k}} }
```

## Tar extractor
We don't know exactly where `__DATA__` is placed within the archive, so we
start by reading 512 bytes and trimming off the leading null bytes from the end
of the bootstrap file section. (This is why we need assumption (3) above.)

Then we enter the main decoding loop, collecting successive file entries and
updating the parsed literate state.

```pl
sub literate_parse($$\%)
{ my ($boot, $key, $unresolved_links) = @_;
  (my $path = $key) =~ s|[^/]*$||;
  $$boot{parsed}{$key}
    = [my @p = map ref() ? $_ : peril::literate::resolve_path "$path$_",
               peril::literate::literate_elements_markdown $key => $$boot{source}{$key}];
  ++$$unresolved_links{$_} for grep !ref() && !exists $$boot{parsed}{$_}, @p;
  delete $$unresolved_links{$key};
  keys %$unresolved_links
     ? undef
     : join '', compile_literate_source 'peril.p.md', %{$$boot{parsed}} }

sub bootstrap()
{ my $image  = peril::image->new;
  my $header = '';
  1 while read DATA, $header, 512 - length $header, length $header;
  $header =~ s/^\0*([^\0])/$1/;

  while (1)
  { 1 while read DATA, $header, 512 - length $header, length $header;
    my ($name, $length) = unpack 'Z100 x24 a12', $header;
    $length = oct $length;
    my $source = '';
    1 while read DATA, $source, $length - length $source, length $source;

    if ($image->add($name, $source)->is_complete) {
      $image->eval($header)->main(@ARGV);
    }

    # Parse the new entry, compile the full image if we have enough 
    if (defined($header = literate_parse $image, $name)) {
    }

    # Read+discard the rest of the 512-byte block
    $header = ''; 1 while read DATA, $header, (-$length & 511) - length $header;
    $header = '' } }
```
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         