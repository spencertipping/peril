# `gen` blocks
This is the entry point for `gen{}`, which uses a lot of dynamically-scoped
state under the hood. The first thing we need is a global to track the current
block; any abstract values will add themselves to that block's owned-list when
allocated.

```perl
package peril::gen::block;
our @scope;
sub require_scope { die "peril::gen: not inside a gen{} block" unless @scope }
sub enter_block   { push @scope, peril::gen::block->new; current }
sub exit_block    { require_scope; pop @scope }
sub current       { require_scope; $scope[-1] }

package peril;
sub gen(&)
{ peril::gen::block::enter_block;
  my $return = shift->();
  peril::gen::block::exit_block->returning($return) }
```

## Block objects
A block tracks every abstract expression evaluated within it, and in particular
the evaluation order in which they were encountered. It inherits this ordering
from Perl.

```perl
package peril::gen::block;
push our @ISA, 'peril::gen';
sub new { current->own(my $self = bless { vs => [] }, $_[0]); $self }
sub own { my $self = shift; push @{$$self{vs}}, @_; $self }
```
