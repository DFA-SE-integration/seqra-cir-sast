void *internalSource();
void internalSink(void *data);

void *source() {
    return internalSource();
}

void sink(void *data) {
    internalSink(data);
}
