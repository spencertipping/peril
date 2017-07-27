# Buffered input stream
Wraps an input stream of some kind with a mutable in-memory buffer. This class
is reasonably quick for small buffered reads and it lets you access the buffer
directly.

```perl
package peril;
use peril::gen;

# how to parameterize this?
# structurally, we've got something like:
#
#   ibuf = IO a => { io     :: a b,
#                    buf    :: array(b)->ref,
#                    offset :: uint32 }
#
# there's no runtime polymorphism going on here, so this can all be static. but
# that requires IO to know that it's a parameterized type up front.

use struct ibuf => ( io     => _,
                     buf    => array(_)->ref,
                     offset => uint32 );
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
ibuf->compact_buffer_ = qe
{ my ($self) = @_;                      # receiver is an lvalue
  return_($self)->unless_($self->offset);
  $self->buf->shift_($self->offset);
  $self->offset(0) };

ibuf->read_into_ = qe
{ my ($self, $xs, $length, $offset) = @_;
  var $length;                          # restrict assign into args? maybe
  return_(0)->unless_($length > 0);
  if_ length_($io->buf),
    qe { my $available = -$io->offset + length_ $io->buf;
         if_ $length > $available, qe {$length = $available};
         # ???
         $io->offset += $length;
         if_ $io->offset >= length_($io->buf) >> 1, qe {$io->compact_buffer_};
         $length },
    qe { $self->io->read_($xs, $length, $offset) } };

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
ibuf->buffer_size_ = qe
{ my ($self) = @_; -$self->offset + $self->buf->length_ };

ibuf->buffer_expand_by_ = qe
{ my ($self, $n) = @_;
  $self->read_into_exactly_($self->buf, $n, $self->buf->length_);
  $self };

ibuf->buffer_expand_to_ = qe
{ my ($self, $n) = @_; $self->buffer_expand_by_($self->buffer_size_ - $n) };

sub buffer
{ my $self = shift;
  return $self->compact_buffer->{read_buffer} unless @_;
  $$self{read_buffer} = shift;
  $$self{read_offset} = 0;
  $self }

sub buffer_ref
{ my ($self) = @_; \($self->compact_buffer->{read_buffer}) }
```
