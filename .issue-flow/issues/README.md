# issue-flow issues

Issue plans live here.

Default layout:

```text
.issue-flow/issues/<issue-number>-<issue-title>/plan/*.md
```

The `plan` action writes or updates plan files under this directory. The `build` action reads every matching plan file before editing code.
