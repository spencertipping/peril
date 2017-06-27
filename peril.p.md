# Peril
Peril is a distributed shell that can be safely edited while it's running. This
file is the root of its literate source code, which is compiled into Perl when
you run the shell. You should probably skim through these docs to get a sense
for how Peril works.

## Core stuff: literate source and runtime edits
Peril uses Markdown as a source code format, or more precisely as a declarative
wrapper around executable code. This is a lot like the way Ruby wraps
metaprogramming using declarative syntax:

```
# declarative
class Foo
  def bar
    puts "hi"
  end
end

# metaprogramming
Foo = Class.new do |c|
  c.class_eval do
    define_method(:bar) do
      puts "hi"
    end
  end
end
```

Like Ruby, Peril has a metaprogramming interface that supports method calls
translated from the declarative Markdown inputs. Here's how that translation
works:

- Markdown links become calls to `$compiler->link("destination")`
- A fenced code block whose language is X becomes `$compiler->code("X", "block")`
- **TODO:** possibly more stuff, like regular paragraphs

**TODO:** Think this through a bit; should we have this direct
Markdown->compiler link, or should it be Markdown->records->compiler? This is a
good opportunity to test the "linearize things" abstraction.
