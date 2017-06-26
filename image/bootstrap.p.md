# Archive bootstrapping
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
