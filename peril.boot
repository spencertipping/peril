#line 15 "image/literate.p.md"
sub literate_elements_markdown($$) {
  my ($file, @lines) = ($_[1], "", split /\n/, $_[0]);
  my @toggles        = (0, grep $lines[$_] =~ /^\s*\`\`\`/, 1..$#lines);
  (map((join("\n", @lines[$toggles[$_-2]..$toggles[$_-1]]) =~ /\]\(([^)]+\.p\.md)\)/g,
        [$toggles[$_-1] + 1,
         $lines[$toggles[$_-1]] =~ /^\s*\`\`\`(.*)$/,
         join "\n", @lines[$toggles[$_-1]+1..$toggles[$_]-1]]),
       grep !($_ & 1), 1..$#toggles),
   join("\n", @lines[$toggles[-1]..$#lines]) =~ /\]\(([^)]+\.p\.md)\)/g);
}
