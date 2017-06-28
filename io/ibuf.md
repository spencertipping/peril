# Buffered input stream
Wraps an input stream of some kind with a mutable in-memory buffer. This class
is reasonably quick for small buffered reads and it lets you access the buffer
directly.

```perl
package peril::ibuf;
use bytes;
use overload qw( ${} buffer_ref );
sub new
{ my ($class, $input) = @_;
  bless {input       => $input,
         read_buffer => '',
         read_offset => 0,    # index of first valid buffer byte
         error       => 0}, $class }
```

## Basic reads
`read_into($buf, $length, $offset)` is just like Perl's `read()`, but if
there's buffered data we read that first. We also read only buffered data or
file data; no mixed reads will happen here.

The buffer is resized somewhat lazily: we only copy memory if we've consumed
half or more of what we're storing. This means small reads against a large
buffer aren't a problem; most of them just involve incrementing the buffer
offset.

```perl
sub allocate_read_buffer { "\0" x $_[1] }
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
  { my $n = $$self{input}->read($_[1], $length, $offset);
    $$self{error} = $! unless defined $n;
    $n } }
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
