# IO and codegen
Peril is all about IO, and in particular making sure not to introduce
unnecessary overhead.

Example gen template, maybe:

```perl
my $f = gen_ {
  my $x = $_[0];
  my $y = $x;
  if_($y >= 10)
  then_ {
    $y += 5;
    return_ $y;
  }
  else_ {
    return_ 0;
  }

  my $h = _{a => 4, b => 5};
  my $v = $$h{a};         # q: how do we do this? (tied object, maybe)

  my $xs = _[1,2,3];
  grep_ {$_ % 2} @$xs;    # ...and this?
};
```

Probably able to target Perl, C, JS, and possibly more.
