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

The bootstrap logic includes the [literate Markdown compiler](literate.p.md).

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
package peril::boot;

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
     : join '', peril::boot_fns::compile_literate_source 'peril.p.md', %{$$boot{parsed}} }

sub bootstrap()
{ my %unresolved_links;
  my $boot   = bless {source => {}, parsed => {}}, 'peril::image';
  my $header = '';
  1 while read DATA, $header, 512 - length $header, length $header;
  $header =~ s/^\0*([^\0])/$1/;

  while (1)
  { 1 while read DATA, $header, 512 - length $header, length $header;
    my ($name, $length) = unpack 'Z100 x24 a12', $header;
    $length = oct $length;
    $$boot{source}{$name} = '';
    1 while read DATA, $$boot{source}{$name},
                 $length - length $$boot{source}{$name},
                 length $$boot{source}{$name};

    # Parse the new entry, compile the full image if we have enough 
    if (defined($header = literate_parse $boot, $name, %unresolved_links)) {
      $boot->boot_code($header);
      eval $header; die $@ if $@;
      exit $boot->main(@ARGV);
    }

    # Read+discard the rest of the 512-byte block
    $header = ''; 1 while read DATA, $header, (-$length & 511) - length $header;
    $header = '' } }
```
