# Literate compiler
Takes a Markdown document (as a string) and a filename, and returns a list of
items describing code and links that were encountered. Each item is one of the
following:

- `"link/destination.p.md"`
- `[line_number, "language", "code"]`

Indented blocks of code are not parsed; this function just finds fenced blocks.

Also note that the link parser only returns links that point to things with a
`.p.md` suffix, and doesn't parse parentheses within the link ref.

```pl
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
```
