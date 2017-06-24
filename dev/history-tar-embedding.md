### Breaking the first entry: checksum
Now obviously it isn't ideal for `tar` to try to create a file with this name,
so we need to mangle the first entry enough to make `tar` skip it but not
enough to make it bail. Trying a couple of things:

```sh
$ echo foo > foo
$ echo bar > bar
$ tar -c foo bar | sed 1s/^foo/bif/ | tar -t
tar: This does not look like a tar archive
tar: Skipping to next header
bar
tar: Exiting with failure status due to previous errors
```

Some error messages, but `tar` skips over the first file because we've mangled
the checksum. At first I thought a better option would be to use the prefix to
set an absolute filepath, which `tar` will reject by default, but it implements
rejection by just stripping the `/` from the beginning -- not quite what we
need here.

```sh
$ tar -Pc /vmlinuz foo | tar -t
tar: Removing leading `/' from member names
/vmlinuz
foo
```

It also turns out that my break-the-checksum idea doesn't work with the OSX
version of `tar`:

```sh
$ tar -c foo bar | sed 1s/^foo/bif/ | ssh $joyces_machine 'tar -t'
tar: Failed to set default locale
tar: Unrecognized archive format
tar: Error exit delayed from previous errors.
```

It completely bails when the checksum doesn't line up. This means we need to
come up with something else.

### Using reserved type flag values
"You got a tarball from the future" is probably a more realistic case than "you
got a file with corrupted data," so we may have more luck using that strategy.
To test this, we're going to need a tarfile header generator.

#### tar header encoder
```sh
$ ni 1p'use constant tar_header_pack =>
          q{ a100 a8 a8 a8 a12 a12 a8 c a100 a6 a2 a32 a32 a8 a8 a155 };

        sub octify {sprintf "\%0$_[1]o", $_[0]}
        sub tar_header {
          my ($filename, $mode, $uid, $gid, $size, $mtime, # checksum (generated)
              $ftype, $linkname, $magic, $ustar_version, $uname, $gname, $major,
              $minor, $prefix) = @_;
          $_ = octify $_, 7  for $mode, $uid, $gid, $major, $minor;
          $_ = octify $_, 11 for $size, $mtime;
          my $checksum_template = pack tar_header_pack,
            my @fs =
              ($filename, $mode, $uid, $gid, $size, $mtime, "        ", $ftype,
               $linkname, $magic, $ustar_version, $uname, $gname,
               $major, $minor, $prefix);
          $fs[6] = octify unpack("%24C*", $checksum_template), 6;
          pack tar_header_pack, @fs;
        }

        print pack"a512", tar_header(
          "#!/usr/bin/env perl\0\n<<'\'_\'';\n<script type=\"peril\">",
          0644,
          0,
          0,
          512,
          time(),
          0,
          "",
          "ustar\0",
          "00",
          "spencertipping",
          "spencertipping",
          0,
          0,
          "peril_artifact_delete_this_directory");
        print pack"a512", qq{</script>\n_\nprint "perl works!\n"\n__END__\n}
                        . qq{<script>alert("webpage works")</script>};
        print "\0"x1024;
        ()' \>test-tarfile
```

Checking each format:

```sh
$ tar -tf test-tarfile
peril_artifact_delete_this_directory/#!/usr/bin/env perl

$ chmod +x test-tarfile
$ ./test-tarfile
perl works!

$ ln -s test-tarfile{,.html}    # open with a browser
```

I haven't found any way to get `tar` to skip a file without bailing out, so I'm
going to have to live with relegating the file to a directory named
`delete_me`.
