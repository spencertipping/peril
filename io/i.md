# Input stream
The input side of I/O. Peril separates this from [output](o.md#) because
bidirectional IO objects aren't terribly common, and typically you'd treat the
read and write sides separately anyway. This class provides shared methods but
doesn't specify an implementation.

```perl
package peril::i;
```

## `read` utilities
Note that nothing about `read()` requires the units to be specified in bytes;
Peril uses this generality extensively as a way to decode things inside the IO
layer.

- `read_into_exactly($buf, $length, $offset)`: calls `read_into()` until EOF,
  error, or we've read `$length` in total.
- `read($length)`: `read_into_exactly` against a temporary buffer, then return
  that buffer.

```perl
sub read_into_exactly
{ my ($self, undef, $length, $offset) = @_;
  $offset ||= 0;
  my $n = 0;
  $n += $r while $r = $self->read_into($_[1], $length - $n, $offset + $n);
  $n }

sub read
{ my ($self, $n) = @_;
  my $buf = $self->allocate_read_buffer($n);
  $self->read_into_exactly($buf, $n);
  $buf }
```
