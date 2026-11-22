/* Plain-C checks for libroute (make test). Exit code 0 = all passed. */
#include "libroute.h"

#include <stdio.h>
#include <stdlib.h>

static int failures = 0;

#define CHECK(cond, msg)                                              \
    do {                                                              \
        if (!(cond)) {                                                \
            fprintf(stderr, "FAIL %s:%d  %s\n", __FILE__, __LINE__, msg); \
            failures++;                                               \
        }                                                             \
    } while (0)

/* Every step moves exactly one cell up/down/left/right, stays on free cells, and ends where expected. */
static int is_valid_path(int w, int h, const uint8_t *grid, const int *xy, int len,
                         int sx, int sy, int gx, int gy) {
    if (len < 1 || xy[0] != sx || xy[1] != sy || xy[2 * (len - 1)] != gx || xy[2 * (len - 1) + 1] != gy) return 0;
    for (int i = 0; i < len; i++) {
        int x = xy[2 * i], y = xy[2 * i + 1];
        if (x < 0 || x >= w || y < 0 || y >= h || grid[y * w + x]) return 0;
        if (i > 0 && abs(x - xy[2 * i - 2]) + abs(y - xy[2 * i - 1]) != 1) return 0;
    }
    return 1;
}

static void open_grid_gives_manhattan_length(void) {
    uint8_t grid[5 * 4] = {0};
    int out[2 * 64];
    int len = route(5, 4, grid, 0, 0, 4, 3, out, 64);
    CHECK(len == 8, "5x4 open grid corner to corner = 7 steps = 8 points");
    CHECK(is_valid_path(5, 4, grid, out, len, 0, 0, 4, 3), "open grid path is contiguous");
}

static void wall_forces_detour(void) {
    /* x = 2 is a wall except at y = 4 (bottom). Start (0,0), goal (4,0). */
    enum { W = 5, H = 5 };
    uint8_t grid[W * H] = {0};
    for (int y = 0; y < H - 1; y++) grid[y * W + 2] = 1;
    int out[2 * 64];
    int len = route(W, H, grid, 0, 0, 4, 0, out, 64);
    CHECK(len == 13, "detour through the bottom gap: 4 down, 4 right, 4 up = 12 steps");
    CHECK(is_valid_path(W, H, grid, out, len, 0, 0, 4, 0), "detour path avoids the wall");
}

static void unreachable_goal_returns_no_path(void) {
    enum { W = 3, H = 3 };
    uint8_t grid[W * H] = {0};
    for (int y = 0; y < H; y++) grid[y * W + 1] = 1;
    int out[2 * 16];
    CHECK(route(W, H, grid, 0, 0, 2, 2, out, 16) == ROUTE_NO_PATH, "full wall: no path");
}

static void start_equals_goal_is_one_point(void) {
    uint8_t grid[4] = {0};
    int out[2] = {-1, -1};
    CHECK(route(2, 2, grid, 1, 1, 1, 1, out, 1) == 1 && out[0] == 1 && out[1] == 1, "start == goal");
}

static void small_buffer_reports_too_long(void) {
    uint8_t grid[10] = {0};
    int out[2 * 3];
    CHECK(route(10, 1, grid, 0, 0, 9, 0, out, 3) == ROUTE_ERR_TOO_LONG, "10-point path into a 3-point buffer");
}

static void bad_arguments_are_rejected(void) {
    uint8_t grid[4] = {0, 1, 0, 0};
    int out[8];
    CHECK(route(0, 2, grid, 0, 0, 1, 1, out, 4) == ROUTE_ERR_ARGS, "zero width");
    CHECK(route(2, 2, NULL, 0, 0, 1, 1, out, 4) == ROUTE_ERR_ARGS, "NULL grid");
    CHECK(route(2, 2, grid, 0, 0, 1, 1, NULL, 4) == ROUTE_ERR_ARGS, "NULL out");
    CHECK(route(2, 2, grid, 0, 0, 2, 0, out, 4) == ROUTE_ERR_ARGS, "goal out of bounds");
    CHECK(route(2, 2, grid, -1, 0, 1, 1, out, 4) == ROUTE_ERR_ARGS, "start out of bounds");
    CHECK(route(2, 2, grid, 0, 0, 1, 0, out, 4) == ROUTE_ERR_ARGS, "goal on a blocked cell");
    CHECK(route(2, 2, grid, 0, 0, 1, 1, out, 0) == ROUTE_ERR_ARGS, "max_len 0");
}

static void large_grid_with_maze_is_optimal(void) {
    /* Serpentine: walls on every other row, alternating gap at the right / left end. */
    enum { W = 200, H = 199 };
    uint8_t *grid = calloc(W * H, 1);
    for (int y = 1; y < H; y += 2) {
        for (int x = 0; x < W; x++) grid[y * W + x] = 1;
        int gap = ((y / 2) % 2 == 0) ? W - 1 : 0;
        grid[y * W + gap] = 0;
    }
    int cap = W * H;
    int *out = malloc(2 * (size_t) cap * sizeof *out);
    int len = route(W, H, grid, 0, 0, 0, H - 1, out, cap);
    /* 100 corridor rows (y even), each crossed end to end (W - 1 steps), plus H - 1 vertical steps through gaps. */
    int expected_steps = ((H + 1) / 2) * (W - 1) + (H - 1);
    CHECK(len == expected_steps + 1, "serpentine maze has the exact optimal length");
    CHECK(is_valid_path(W, H, grid, out, len, 0, 0, 0, H - 1), "serpentine path is valid");
    free(out);
    free(grid);
}

int main(void) {
    open_grid_gives_manhattan_length();
    wall_forces_detour();
    unreachable_goal_returns_no_path();
    start_equals_goal_is_one_point();
    small_buffer_reports_too_long();
    bad_arguments_are_rejected();
    large_grid_with_maze_is_optimal();
    if (failures) {
        fprintf(stderr, "%d check(s) failed\n", failures);
        return 1;
    }
    printf("libroute: all checks passed\n");
    return 0;
}
