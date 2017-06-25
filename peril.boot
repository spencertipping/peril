#line 14 "image/bootstrap.p.md"
package peril::boot;
use strict;
use warnings;
use 5.008;
#line 15 "image/literate.p.md"
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
#line 26 "image/bootstrap.p.md"
our %source;
our %parsed;
our %unresolved_links;
#line 37 "image/bootstrap.p.md"
sub compile_literate_source($);
sub compile_literate_source($)
{ local $_; map ref() ? $$_[1] =~ /^pl/ ? "#line $$_[0] \"$_[0]\"\n$$_[2]\n" : ()
                      : compile_literate_source $_, @{$parsed{$_[0]}} }

sub literate_parse($)
{ (my $path = $_[0]) =~ s|[^/]*$||;
  $parsed{$_[0]} = [my @p = map ref() ? $_ : resolve_path "$path$_",
                            literate_elements_markdown $_[0] => $source{$_[0]}];
  ++$unresolved_links{$_} for grep !ref() && !exists $parsed{$_}, @p;
  delete $unresolved_links{$_[0]};
  keys %unresolved_links
     ? undef
     : join '', compile_literate_source 'peril.p.md' }
#line 62 "image/bootstrap.p.md"
sub bootstrap()
{ my $header = '';
  1 while read DATA, $header, 512 - length $header, length $header;
  $header =~ s/^\0*([^\0])/$1/;

  while (1)
  { 1 while read DATA, $header, 512 - length $header, length $header;
    my ($name, $length) = unpack 'Z100 x24 a12', $header;
    $length = oct $length;
    $source{$name} = '';
    1 while read DATA, $source{$name}, $length - length $source{$name},
                 length $source{$name};

    # Parse the new entry, compile the full image if we have enough 
    eval, die $@ if defined($_ = literate_parse $name);

    # Read+discard the rest of the 512-byte block
    $header = ''; 1 while read DATA, $header, (-$length & 511) - length $header;
    $header = '' } }
bootstrap;
__DATA__
peril.p.md                                                                                          0000644 0001750 0001750 00000000204 13123357327 015621  0                                                                                                    ustar   spencertipping                  spencertipping                                                                                                                                                                                                         # Peril
## Base layer
- [Image representation](image.p.md)

```pl
print encode_image('peril.p.md', %peril::boot::source);
exit;
```
                                                                                                                                                                                                                                                                                                                                                                                            image.p.md                                                                                          0000644 0001750 0001750 00000004152 13123351540 015565  0                                                                                                    ustar   spencertipping                  spencertipping                                                                                                                                                                                                         # Peril image
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
                                                                                                                                                                                                                                                                                                                                                                                                                      image/                                                                                              0000755 0001750 0001750 00000000000 13123750676 015020  5                                                                                                    ustar   spencertipping                  spencertipping                                                                                                                                                                                                         image/literate.p.md                                                                                 0000644 0001750 0001750 00000002063 13123355325 017402  0                                                                                                    ustar   spencertipping                  spencertipping                                                                                                                                                                                                         # Literate compiler
Takes a Markdown document (as a string) and a filename, and returns a list of
items describing code and links that were encountered. Each item is one of the
following:

- `"link/destination.p.md"`
- `[line_number, "language", "code"]`

Indented blocks of code are not parsed; this function just finds fenced blocks.

Also note that the link parser only returns links that point to things with a
`.p.md` suffix, and doesn't parse parentheses within the link ref.

```pl
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
                                                                                                                                                                                                                                                                                                                                                                                                                                                                             image/generator.p.md                                                                                0000644 0001750 0001750 00000010344 13123750511 017554  0                                                                                                    ustar   spencertipping                  spencertipping                                                                                                                                                                                                         # Image generator
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
                                                                                                                                                                                                                                                                                            image/bootstrap.p.md                                                                                0000644 0001750 0001750 00000005161 13123357150 017606  0                                                                                                    ustar   spencertipping                  spencertipping                                                                                                                                                                                                         # Archive bootstrapping
We can make a few simplifying assumptions:

1. All Peril code is encoded before anything else.
2. Peril code is all stored as regular files with tar attributes we can mostly
   ignore.
3. All bytes after `__DATA__\n` are null until the next tarfile entry.
4. The root Markdown file is encoded first.

(4) is required because otherwise the dependency graph could be completed
early.

```pl
package peril::boot;
use strict;
use warnings;
use 5.008;
```

The bootstrap logic includes the [literate Markdown compiler](literate.p.md).

We also maintain a bit of state about the image we're booting from. This is
used later on, mostly when we generate derivatives.

```pl
our %source;
our %parsed;
our %unresolved_links;
```

## Literate compiler/dependency graph
We parse incrementally as we decode stuff, tracking unresolved dependencies as
we go. Once we have a full graph we join the code into a big string and
evaluate it.

```pl
sub compile_literate_source($);
sub compile_literate_source($)
{ local $_; map ref() ? $$_[1] =~ /^pl/ ? "#line $$_[0] \"$_[0]\"\n$$_[2]\n" : ()
                      : compile_literate_source $_, @{$parsed{$_[0]}} }

sub literate_parse($)
{ (my $path = $_[0]) =~ s|[^/]*$||;
  $parsed{$_[0]} = [my @p = map ref() ? $_ : resolve_path "$path$_",
                            literate_elements_markdown $_[0] => $source{$_[0]}];
  ++$unresolved_links{$_} for grep !ref() && !exists $parsed{$_}, @p;
  delete $unresolved_links{$_[0]};
  keys %unresolved_links
     ? undef
     : join '', compile_literate_source 'peril.p.md' }
```

## Tar extractor
We don't know exactly where `__DATA__` is placed within the archive, so we
start by reading 512 bytes and trimming off the leading null bytes from the end
of the bootstrap file section. (This is why we need assumption (3) above.)

Then we enter the main decoding loop, collecting successive file entries and
updating the parsed literate state.

```pl
sub bootstrap()
{ my $header = '';
  1 while read DATA, $header, 512 - length $header, length $header;
  $header =~ s/^\0*([^\0])/$1/;

  while (1)
  { 1 while read DATA, $header, 512 - length $header, length $header;
    my ($name, $length) = unpack 'Z100 x24 a12', $header;
    $length = oct $length;
    $source{$name} = '';
    1 while read DATA, $source{$name}, $length - length $source{$name},
                 length $source{$name};

    # Parse the new entry, compile the full image if we have enough 
    eval, die $@ if defined($_ = literate_parse $name);

    # Read+discard the rest of the 512-byte block
    $header = ''; 1 while read DATA, $header, (-$length & 511) - length $header;
    $header = '' } }
```
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               