# Peril: Know your favorite color. Or else.
**WARNING:** Nowhere near done yet; don't try to use this.

Peril is a collaborative notebook editor and networked UNIX shell. It's
designed to replace bash, tools like Jupyter, and various ad-hoc workflows
involving `ssh` and `rsync`. Like all great tools, Peril ~was~ will have been
written out of frustration and delusional perfectionism.

## Installation [TODO]
```sh
$ curl https://raw.githubusercontent.com/spencertipping/peril/master/peril | perl -
```

Equivalently, assuming `~/bin` is in your `$PATH`:

```sh
$ curl https://raw.githubusercontent.com/spencertipping/peril/master/peril > ~/bin/peril \
  && chmod 755 ~/bin/peril
```

You can also give Peril a try in a preinstalled environment:

```sh
$ docker run --rm -it spencertipping/peril
```

## Documentation
See the [literate source code](peril.md).
