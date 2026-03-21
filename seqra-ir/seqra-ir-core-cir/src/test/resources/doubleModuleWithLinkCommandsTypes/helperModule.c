#include "header.h"
//#include <stdlib.h>
//#include <assert.h>

int global_val = 42;

void *malloc(int size);
void free(void *ptr);

data_holder *source() {
    return malloc(sizeof(data_holder));
}

void sink(data_holder *holder) {
    global_val = holder->data;
//    assert(holder->data == global_val);
    free(holder);
}
