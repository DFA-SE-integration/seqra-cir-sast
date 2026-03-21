#include <stdlib.h>
#include <assert.h>

typedef struct data_holder {
    int data;
} data_holder;

int global_val = 42;

data_holder *source() {
    return malloc(sizeof(data_holder));
}

void sink(data_holder *holder) {
//    global_val = holder->data;
    assert(holder->data == global_val);
    free(holder);
}
