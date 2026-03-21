void *source();
void sink(void *);

int main() {
    void *x = source();
    sink(x);
}
