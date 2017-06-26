# Peril
## Base layer
- [Image representation](image.p.md)

```pl
sub peril::image::main {
  print encode_image('peril.p.md', %peril::boot::source);
  0;
}
```
