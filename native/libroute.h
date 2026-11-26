/*
 * libroute — A* shortest path on a 4-connected occupancy grid. Called from Java through FFM (owner: D).
 */
#ifndef DOSERUNNER_LIBROUTE_H
#define DOSERUNNER_LIBROUTE_H

#include <stdint.h>

#if defined(_WIN32)
#  define ROUTE_API __declspec(dllexport)
#else
#  define ROUTE_API __attribute__((visibility("default")))
#endif

/* Return codes (a positive value is the number of points written). */
#define ROUTE_NO_PATH        0   /* goal unreachable */
#define ROUTE_ERR_ARGS      -1   /* bad size, NULL pointer, start/goal outside the grid or on a blocked cell */
#define ROUTE_ERR_TOO_LONG  -2   /* a path exists but has more than max_len points: retry with a bigger buffer */
#define ROUTE_ERR_NOMEM     -3   /* allocation failed */

/*
 * Shortest path from (sx, sy) to (gx, gy).
 *
 *   w, h     grid size in cells (w * h must fit in an int)
 *   grid     w * h bytes, row-major (cell (x, y) is grid[y * w + x]); 0 = free, anything else = blocked
 *   out_xy   caller-owned buffer of 2 * max_len ints; receives x0, y0, x1, y1, ... from start to goal, both included
 *   max_len  capacity of out_xy in points (not in ints)
 *
 * Moves are up/down/left/right with cost 1; the heuristic is Manhattan distance, so the path is optimal.
 * The function is re-entrant: no global state, all scratch memory is freed before returning.
 */
ROUTE_API int route(int w, int h, const uint8_t *grid,
                    int sx, int sy, int gx, int gy,
                    int *out_xy, int max_len);

#endif
