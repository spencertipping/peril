# Image generator
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

### Bootstrap code
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

The bootstrap code itself is described in more detail [here](bootstrap.p.md).

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
