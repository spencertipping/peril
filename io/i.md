# Input stream
The input side of I/O. Peril separates this from [output](o.md#) because
bidirectional IO objects aren't terribly common, and typically you'd treat the
read and write sides separately anyway. This class provides shared methods but
doesn't specify an implementation.

```perl
package peril::i;
use peril::gen;
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
read_into_exactly(io, _, uint32, uint32) = qe
{ my ($io, $buf, $length, $offset) = @_;
  var(uint32 => my $n) = 0;
  var uint32 => my $r;
  while_ qe {$r = read_into_($io, $buf, $length - $n, $offset + $n)},
         qe {$n += $r};
  $n };

# problem: `io` needs to be parameterized if we want allocate_read_buffer_ to
# do the right thing here. Do we have parameterized types?
read(io, uint32) = qe
{ my ($io, $n) = @_;
  read_into_exactly $io, my $buf = allocate_read_buffer_($io, $n), $n, 0;
  $buf };
```
