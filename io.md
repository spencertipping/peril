# IO and codegen
Peril is all about IO, and in particular making sure not to introduce
unnecessary overhead.

Example gen template, maybe:

```perl
my $f = gen {
  args uint32 => my $x;
  var my $y = $x;
  ($y >= 10)
    ->if(gen {$y += 5; $y->return})
    ->else(gen {return_ 0});

  var my $h  = {a => 4, b => 5};
  var my $v  = $$h{a};
  var my $xs = [1, 2, 3];
  $xs->grep(gen {$_ % 2});
};
```
