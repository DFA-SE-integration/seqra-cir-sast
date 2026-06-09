/* Controlled container-mediated UAF: same value lives in two GHashTables;
 * freed via a foreach_remove callback on one, then read via a foreach callback
 * on the other. Exercises the g_hash_table_foreach[_remove] KLEE model. */
typedef unsigned long gsize; typedef unsigned int guint; typedef int gboolean; typedef void* gpointer;
typedef void (*GHFunc)(gpointer, gpointer, gpointer);
typedef gboolean (*GHRFunc)(gpointer, gpointer, gpointer);
extern gpointer g_malloc(gsize);
extern void g_free(gpointer);
extern gpointer g_hash_table_new(gpointer, gpointer);
extern void g_hash_table_insert(gpointer, gpointer, gpointer);
extern guint g_hash_table_foreach_remove(gpointer, GHRFunc, gpointer);
extern void g_hash_table_foreach(gpointer, GHFunc, gpointer);

static gboolean free_val(gpointer k, gpointer v, gpointer u) { (void)k; (void)u; g_free(v); return 1; }
static void read_val(gpointer k, gpointer v, gpointer u) { (void)k; int *p = (int *)v; *(int *)u += p[0]; }

int entry_container_uaf(void) {
    gpointer a = g_hash_table_new(0, 0);
    gpointer b = g_hash_table_new(0, 0);
    int *p = (int *)g_malloc(16);
    p[0] = 7;
    g_hash_table_insert(a, (gpointer)1, p);   /* same value p in both tables */
    g_hash_table_insert(b, (gpointer)1, p);
    int sum = 0;
    g_hash_table_foreach_remove(a, free_val, 0);  /* free_val: g_free(p) */
    g_hash_table_foreach(b, read_val, &sum);      /* read_val: reads p[0] after free -> UAF */
    return sum;
}
