extern void *malloc(unsigned long);
int *x = (int *)malloc(4);

int main() { *x = 10; }
