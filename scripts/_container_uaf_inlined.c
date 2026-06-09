/* Premise check for option B: what a "foreach -> direct callback call" CIR pass
 * would produce. The two g_hash_table_foreach[_remove] calls are replaced by
 * direct calls to their callbacks on a shared representative element (p), the
 * same object that lived in both tables. IFDS can now step into the callbacks
 * (they have bodies) and KLEE executes them -> the container-mediated UAF
 * (free in one callback, read in the other) becomes detectable. */
typedef unsigned long gsize; typedef int gboolean; typedef void* gpointer;
extern gpointer g_malloc(gsize);
extern void g_free(gpointer);

static gboolean free_val(gpointer k, gpointer v, gpointer u) { (void)k; (void)u; g_free(v); return 1; }
static void read_val(gpointer k, gpointer v, gpointer u) { (void)k; int *p = (int *)v; *(int *)u += p[0]; }

int entry_container_uaf(void) {
    int *p = (int *)g_malloc(16);
    p[0] = 7;
    /* inlined foreach_remove(a, free_val): a's representative element is p */
    free_val((gpointer)1, (gpointer)p, 0);
    /* inlined foreach(b, read_val): b shares the same element p */
    int sum = 0;
    read_val((gpointer)1, (gpointer)p, &sum);  /* reads p[0] after g_free(p) -> UAF */
    return sum;
}
