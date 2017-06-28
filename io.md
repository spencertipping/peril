# IO and codegen
Peril is all about IO, and in particular making sure not to introduce
unnecessary overhead.

Example gen template, maybe:

```perl
my $x = $input;
my $y = $x;
if_($y >= 10)
then_ {
  $y += 5;
  return_ $y;
}
else_ {
  return_ 0;
}

my $xs = gen_ [1,2,3];
grep_ {$_ % 2} @$xs;
```

Probably able to target Perl, C, JS, and possibly more.
