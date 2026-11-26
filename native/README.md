# native/ — C sources called through FFM (owner: D)

- `libroute.c` — A* on a grid, exported as
  `int route(int w, int h, const uint8_t* grid, int sx, int sy, int gx, int gy, int* out_xy, int max_len)`
  plus an upcall variant that asks Java whether a cell is blocked.
- `Makefile` → `libroute.so` (built inside the custody/dispatch Docker images).

Started on days 3–7 of week 1.
