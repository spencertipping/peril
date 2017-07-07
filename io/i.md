# Input stream
The input side of I/O. Peril separates this from [output](o.md#) because
bidirectional IO objects aren't terribly common, and typically you'd treat the
read and write sides separately anyway.

```perl
package peril;
use peril::gen;

use trait 'i';
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
i->read_into_exactly_ = qe
{ my ($self, $buf, $length, $offset) = @_;
  var(uint32 => my $n) = 0;
  var uint32 => my $r;
  while_ qe {$r = $self->read_into_($buf, $length - $n, $offset + $n)},
         qe {$n += $r};
  $n };

i->read_ = qe
{ my ($self, $n) = @_;
  my $buf = $self->allocate_read_buffer_($n)->ref;
  $self->read_into_exactly_($buf, $n, 0);
  $$buf };
```
