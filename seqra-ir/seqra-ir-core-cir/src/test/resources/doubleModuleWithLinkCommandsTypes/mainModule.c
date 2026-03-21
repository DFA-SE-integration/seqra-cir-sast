#include "header.h"

data_holder *source();
void sink(data_holder *);

int global_var = 0;
extern int global_val;

int main() {
    data_holder *s = source();
    s->data = global_val;
    sink(s);
}
