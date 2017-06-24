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
