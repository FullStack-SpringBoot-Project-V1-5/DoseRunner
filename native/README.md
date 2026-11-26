# native/ — C sources called through FFM (owner: D)

| File | What |
|---|---|
| `libroute.h` | contract + return codes |
| `libroute.c` | A* on a 4-connected grid (binary heap, Manhattan heuristic, optimal, re-entrant) |
| `test_route.c` | plain-C checks (`make test`) |
| `Makefile` | `make` → `libroute.so` · `make test` · `make clean` |

```c
int route(int w, int h, const uint8_t* grid, int sx, int sy, int gx, int gy, int* out_xy, int max_len);
```

- `grid`: `w*h` bytes, row-major (`grid[y*w + x]`), `0` = free, anything else = blocked.
- `out_xy`: caller buffer of `2*max_len` ints, filled with `x0,y0,x1,y1,…` from start to goal (both included).
- Returns the number of points (`> 0`), `0` = no path, `-1` bad arguments, `-2` buffer too small, `-3` out of memory.
- Only `route` is exported (`-fvisibility=hidden`); built with `-Wall -Wextra -Wpedantic -Werror`.

`libroute.so` is built and tested inside `deploy/compose/Dockerfile.native` (stage `native`, Ubuntu 24.04 = same glibc
as the JRE image) and copied to `/usr/local/lib`. CI runs `make -C native clean all test`.

**Next:** the Java side (`Route` in `commons-native`, `MemorySegment` for the grid, `Arena.ofConfined()` per call) and the
upcall variant that asks Java whether a cell is blocked. See [docs/NicheIntegration-ffm.md](../docs/NicheIntegration-ffm.md).
