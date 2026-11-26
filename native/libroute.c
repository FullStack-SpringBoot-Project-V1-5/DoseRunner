/*
 * libroute — A* on a grid. See libroute.h for the contract.
 *
 * Open set = binary min-heap ordered by f = g + h (ties: smaller h first, then smaller cell index, so results are
 * deterministic). Stale heap entries are skipped when popped ("lazy deletion") instead of doing decrease-key.
 */
#include "libroute.h"

#include <limits.h>
#include <stdlib.h>

typedef struct {
    int f;
    int h;
    int cell;
} node_t;

typedef struct {
    node_t *items;
    int size;
    int cap;
} heap_t;

static int node_less(const node_t *a, const node_t *b) {
    if (a->f != b->f) return a->f < b->f;
    if (a->h != b->h) return a->h < b->h;
    return a->cell < b->cell;
}

static int heap_push(heap_t *hp, node_t n) {
    if (hp->size == hp->cap) {
        int cap = hp->cap ? hp->cap * 2 : 64;
        node_t *grown = realloc(hp->items, (size_t) cap * sizeof *grown);
        if (!grown) return 0;
        hp->items = grown;
        hp->cap = cap;
    }
    int i = hp->size++;
    while (i > 0) {
        int parent = (i - 1) / 2;
        if (!node_less(&n, &hp->items[parent])) break;
        hp->items[i] = hp->items[parent];
        i = parent;
    }
    hp->items[i] = n;
    return 1;
}

static node_t heap_pop(heap_t *hp) {
    node_t top = hp->items[0];
    node_t last = hp->items[--hp->size];
    int i = 0;
    for (;;) {
        int child = 2 * i + 1;
        if (child >= hp->size) break;
        if (child + 1 < hp->size && node_less(&hp->items[child + 1], &hp->items[child])) child++;
        if (!node_less(&hp->items[child], &last)) break;
        hp->items[i] = hp->items[child];
        i = child;
    }
    if (hp->size > 0) hp->items[i] = last;
    return top;
}

static int manhattan(int x, int y, int gx, int gy) {
    return abs(x - gx) + abs(y - gy);
}

int route(int w, int h, const uint8_t *grid,
          int sx, int sy, int gx, int gy,
          int *out_xy, int max_len) {
    if (w <= 0 || h <= 0 || w > INT_MAX / h || !grid || !out_xy || max_len <= 0) return ROUTE_ERR_ARGS;
    if (sx < 0 || sx >= w || sy < 0 || sy >= h || gx < 0 || gx >= w || gy < 0 || gy >= h) return ROUTE_ERR_ARGS;

    const int n = w * h;
    const int start = sy * w + sx;
    const int goal = gy * w + gx;
    if (grid[start] || grid[goal]) return ROUTE_ERR_ARGS;

    int *g = malloc((size_t) n * sizeof *g);
    int *parent = malloc((size_t) n * sizeof *parent);
    uint8_t *closed = calloc((size_t) n, 1);
    heap_t open = {0};
    int result = ROUTE_NO_PATH;

    if (!g || !parent || !closed) {
        result = ROUTE_ERR_NOMEM;
        goto done;
    }
    for (int i = 0; i < n; i++) {
        g[i] = INT_MAX;
        parent[i] = -1;
    }

    g[start] = 0;
    int h0 = manhattan(sx, sy, gx, gy);
    if (!heap_push(&open, (node_t){h0, h0, start})) {
        result = ROUTE_ERR_NOMEM;
        goto done;
    }

    static const int DX[4] = {1, -1, 0, 0};
    static const int DY[4] = {0, 0, 1, -1};
    int found = 0;

    while (open.size > 0) {
        node_t cur = heap_pop(&open);
        if (closed[cur.cell]) continue;           /* stale entry */
        if (cur.cell == goal) {
            found = 1;
            break;
        }
        closed[cur.cell] = 1;

        int cx = cur.cell % w;
        int cy = cur.cell / w;
        for (int k = 0; k < 4; k++) {
            int nx = cx + DX[k];
            int ny = cy + DY[k];
            if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue;
            int next = ny * w + nx;
            if (grid[next] || closed[next]) continue;
            int tentative = g[cur.cell] + 1;
            if (tentative < g[next]) {
                g[next] = tentative;
                parent[next] = cur.cell;
                int hn = manhattan(nx, ny, gx, gy);
                if (!heap_push(&open, (node_t){tentative + hn, hn, next})) {
                    result = ROUTE_ERR_NOMEM;
                    goto done;
                }
            }
        }
    }

    if (found) {
        int len = g[goal] + 1;                    /* unit costs: points = steps + 1 */
        if (len > max_len) {
            result = ROUTE_ERR_TOO_LONG;
            goto done;
        }
        int i = len - 1;
        for (int c = goal; c != -1; c = parent[c], i--) {
            out_xy[2 * i] = c % w;
            out_xy[2 * i + 1] = c / w;
        }
        result = len;
    }

done:
    free(open.items);
    free(closed);
    free(parent);
    free(g);
    return result;
}
