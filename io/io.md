# IO stream
Low-level stream support for Perl filehandles. The big feature here is
arbitrary-pushback buffering, which makes it possible to do things like "seek
to but don't consume this byte pattern."

```perl
package peril::io;
use bytes;
use overload qw( ${} buffer_ref );
sub new
{ my ($class, $fh) = @_;
  bless {fh          => $fh,
         read_buffer => '',
         read_offset => 0,    # index of first valid buffer byte
         error       => 0}, $class }
```

## Basic reads
At a high level:

- `read_into($buf, $length, $offset)`: just like Perl's `read()`, but if
  there's buffered data we read that first. We also read only buffered data or
  file data; no mixed reads will happen here.
- `read_into_exactly($buf, $length, $offset)`: calls `read_into()` until EOF,
  error, or we've read `$length` in total.
- `read($length)`: `read_into_exactly` against a temporary buffer, then return
  that buffer.

The buffer is resized somewhat lazily: we only copy memory if we've consumed
half or more of what we're storing. This means small reads against a large
buffer aren't a problem; most of them just involve incrementing the buffer
offset.

```perl
sub compact_buffer
{ my ($self) = @_;
  return $self unless $$self{read_offset};
  $$self{read_buffer} = substr $$self{read_buffer}, $$self{read_offset};
  $$self{read_offset} = 0;
  $self }

sub read_into
{ my ($self, undef, $length, $offset) = @_;
  return 0 unless $length > 0;
  $offset ||= 0;
  if (length $$self{read_buffer})
  { my $available = -$$self{read_offset} + length $$self{read_buffer};
    $length = $available if $length > $available;
    substr($_[1], $offset) = substr($$self{read_buffer}, $$self{read_offset}, $length);
    $self->compact_buffer if ($$self{read_offset} += $length) >= length $$self{read_buffer} >> 1;
    $length }
  else
  { my $n = read $$self{fh}, $_[1], $length, $offset;
    $$self{error} = $! unless defined $n;
    $n } }

sub read_into_exactly
{ my ($self, undef, $length, $offset) = @_;
  $offset ||= 0;
  my $n = 0;
  $n += $r while $r = $self->read_into($_[1], $length - $n, $offset + $n);
  $n }

sub read
{ my ($self, $n) = @_;
  my $buf = "\0" x $n;
  $self->read_into_exactly($buf, $n);
  $buf }
```

## Buffer functions
You shouldn't generally assume much about the state of the buffer, but you can
manipulate it if you want to. This can be useful for cases like parsing JPEG
images, which use a two-byte tag to indicate end-of-image but otherwise don't
have length prefixing. In that case you'd want to incrementally load buffer
contents until you encounter the tag, then read exactly that much data.

- `buffer_size()`: the amount of data currently stored in the buffer
- `buffer_expand_by($n)`: reads additional data into the buffer
- `buffer_expand_to($n)`: same thing, calculates the size for you
- `buffer()`: returns the current buffer, compacting it if necessary
- `buffer($new_buf)`: sets the current buffer

```perl
sub buffer_size
{ my ($self) = @_; -$$self{read_offset} + length $$self{read_buffer} }

sub buffer_expand_by
{ my ($self, $n) = @_;
  $self->read_into_exactly($$self{read_buffer}, $n, length $$self{read_buffer});
  $self }

sub buffer_expand_to
{ my ($self, $n) = @_; $self->buffer_expand_by($self->buffer_size - $n) }

sub buffer
{ my $self = shift;
  return $self->compact_buffer->{read_buffer} unless @_;
  $$self{read_buffer} = shift;
  $$self{read_offset} = 0;
  $self }

sub buffer_ref
{ my ($self) = @_; \($self->compact_buffer->{read_buffer}) }
```
