/* glib_klee_models.c — KLEE models for the GLib alloc/free family + a minimal
 * GHashTable so KLEE can dispatch g_hash_table_foreach[_remove] callbacks.
 *
 * KLEE natively tracks libc malloc/free (UAF, double-free). Real C projects
 * allocate through GLib and iterate containers via opaque externals, so KLEE
 * sees neither the alloc/free nor the callback dispatch and misses the bug.
 * These definitions forward GLib allocators/deallocators to libc and implement
 * a tiny array-backed GHashTable whose foreach/foreach_remove actually CALL the
 * callback function pointer for each stored (key,value) — letting KLEE step
 * into destroy/iterate callbacks and detect container-mediated use-after-free.
 *
 * Link with: klee --link-llvm-lib=<this>.bc   (build with clang-16, KLEE=LLVM16)
 *   clang-16 --target=x86_64-pc-linux-gnu  -O1 -emit-llvm -c -o glib_models_x86_64.bc  glib_klee_models.c
 *   clang-16 --target=aarch64-unknown-linux-gnu -O1 -emit-llvm -c -o glib_models_aarch64.bc glib_klee_models.c
 *
 * NOTE: a foreach model only surfaces a bug if the table is POPULATED within
 * KLEE's execution (g_hash_table_insert called in scope). The SARD radius case
 * fills the table from a dictionary file via the flex lexer (fopen/Radiuslex),
 * which KLEE does not replay — so 501116 needs data population beyond this.
 */
typedef unsigned long gsize;
typedef unsigned int  guint;
typedef int           gboolean;
typedef void         *gpointer;
typedef const void   *gconstpointer;

typedef void     (*GHFunc)(gpointer key, gpointer value, gpointer user_data);
typedef gboolean (*GHRFunc)(gpointer key, gpointer value, gpointer user_data);
typedef guint    (*GHashFunc)(gconstpointer key);
typedef gboolean (*GEqualFunc)(gconstpointer a, gconstpointer b);
typedef void     (*GDestroyNotify)(gpointer data);

extern void *malloc(gsize);
extern void *calloc(gsize, gsize);
extern void *realloc(void *, gsize);
extern void  free(void *);
extern void *memcpy(void *, const void *, gsize);
extern gsize strlen(const char *);

/* ---- alloc/free family -> libc (KLEE tracks these) ---- */
void  g_free(void *p)             { free(p); }
void *g_malloc(gsize n)           { return malloc(n); }
void *g_malloc0(gsize n)          { return calloc(1, n); }
void *g_try_malloc(gsize n)       { return malloc(n); }
void *g_try_malloc0(gsize n)      { return calloc(1, n); }
void *g_realloc(void *p, gsize n) { return realloc(p, n); }
void *g_slice_alloc(gsize n)      { return malloc(n); }
void *g_slice_alloc0(gsize n)     { return calloc(1, n); }
void  g_slice_free1(gsize n, void *p) { (void)n; free(p); }

char *g_strdup(const char *s) {
    if (!s) return 0;
    gsize n = strlen(s) + 1;
    char *r = (char *)malloc(n);
    if (r) memcpy(r, s, n);
    return r;
}
void *g_memdup(const void *p, guint n) {
    if (!p) return 0;
    void *r = malloc(n);
    if (r) memcpy(r, p, n);
    return r;
}

/* ---- minimal GHashTable: array of (key,value), linear ops ---- */
typedef struct { gpointer key, value; } GHEntry;
typedef struct {
    GHEntry       *e;
    gsize          len, cap;
    GEqualFunc     key_equal;
    GDestroyNotify key_destroy, val_destroy;
} GHT;

static void ght_grow(GHT *t) {
    if (t->len < t->cap) return;
    t->cap = t->cap ? t->cap * 2 : 8;
    t->e = (GHEntry *)realloc(t->e, t->cap * sizeof(GHEntry));
}
static gsize ght_find(GHT *t, gconstpointer key) {
    for (gsize i = 0; i < t->len; i++) {
        if (t->key_equal ? t->key_equal(t->e[i].key, key) : (t->e[i].key == key))
            return i;
    }
    return (gsize)-1;
}

gpointer g_hash_table_new(GHashFunc h, GEqualFunc eq) {
    (void)h;
    GHT *t = (GHT *)malloc(sizeof(GHT));
    t->e = 0; t->len = 0; t->cap = 0;
    t->key_equal = eq; t->key_destroy = 0; t->val_destroy = 0;
    return t;
}
gpointer g_hash_table_new_full(GHashFunc h, GEqualFunc eq,
                               GDestroyNotify kd, GDestroyNotify vd) {
    GHT *t = (GHT *)g_hash_table_new(h, eq);
    t->key_destroy = kd; t->val_destroy = vd;
    return t;
}
void g_hash_table_insert(gpointer tt, gpointer key, gpointer value) {
    GHT *t = (GHT *)tt;
    gsize i = ght_find(t, key);
    if (i != (gsize)-1) { t->e[i].value = value; return; }
    ght_grow(t);
    t->e[t->len].key = key; t->e[t->len].value = value; t->len++;
}
void g_hash_table_replace(gpointer tt, gpointer key, gpointer value) {
    g_hash_table_insert(tt, key, value);
}
gpointer g_hash_table_lookup(gpointer tt, gconstpointer key) {
    GHT *t = (GHT *)tt;
    gsize i = ght_find(t, key);
    return i == (gsize)-1 ? 0 : t->e[i].value;
}
gboolean g_hash_table_lookup_extended(gpointer tt, gconstpointer key,
                                      gpointer *origkey, gpointer *value) {
    GHT *t = (GHT *)tt;
    gsize i = ght_find(t, key);
    if (i == (gsize)-1) return 0;
    if (origkey) *origkey = t->e[i].key;
    if (value)   *value   = t->e[i].value;
    return 1;
}
guint g_hash_table_size(gpointer tt) { return (guint)((GHT *)tt)->len; }

void g_hash_table_foreach(gpointer tt, GHFunc func, gpointer ud) {
    GHT *t = (GHT *)tt;
    for (gsize i = 0; i < t->len; i++)
        func(t->e[i].key, t->e[i].value, ud);
}
static guint ght_foreach_cond_remove(GHT *t, GHRFunc func, gpointer ud) {
    gsize w = 0; guint removed = 0;
    for (gsize i = 0; i < t->len; i++) {
        if (func(t->e[i].key, t->e[i].value, ud)) removed++;
        else t->e[w++] = t->e[i];
    }
    t->len = w;
    return removed;
}
guint g_hash_table_foreach_remove(gpointer tt, GHRFunc func, gpointer ud) {
    return ght_foreach_cond_remove((GHT *)tt, func, ud);
}
guint g_hash_table_foreach_steal(gpointer tt, GHRFunc func, gpointer ud) {
    return ght_foreach_cond_remove((GHT *)tt, func, ud);
}
gboolean g_hash_table_remove(gpointer tt, gconstpointer key) {
    GHT *t = (GHT *)tt;
    gsize i = ght_find(t, key);
    if (i == (gsize)-1) return 0;
    if (t->key_destroy) t->key_destroy(t->e[i].key);
    if (t->val_destroy) t->val_destroy(t->e[i].value);
    for (gsize j = i + 1; j < t->len; j++) t->e[j - 1] = t->e[j];
    t->len--;
    return 1;
}
void g_hash_table_destroy(gpointer tt) {
    GHT *t = (GHT *)tt;
    if (t->key_destroy || t->val_destroy) {
        for (gsize i = 0; i < t->len; i++) {
            if (t->key_destroy) t->key_destroy(t->e[i].key);
            if (t->val_destroy) t->val_destroy(t->e[i].value);
        }
    }
    free(t->e);
    free(t);
}
