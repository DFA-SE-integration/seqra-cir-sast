find_package(Protobuf CONFIG REQUIRED)
include_directories(${Protobuf_INCLUDE_DIR})

set(PROTOSRC
    enum.proto
    type.proto
    attr.proto
    op.proto
    setup.proto
    model.proto
    alias.proto
)

add_library(proto OBJECT ${PROTOSRC})

protobuf_generate(
    TARGET proto
    IMPORT_DIRS .
    PROTOS
    ${PROTOSRC}
)

target_link_libraries(
    proto PRIVATE
    protobuf::libprotobuf
)

set(TRACE_PROTO_SRC
    trace/ap.proto
    trace/common.proto
    trace/fact.proto
    trace/method.proto
    trace/trace.proto
    trace/result.proto
)

add_library(trace_proto OBJECT ${TRACE_PROTO_SRC})
protobuf_generate(
    TARGET trace_proto
    IMPORT_DIRS trace
    PROTOS
    ${TRACE_PROTO_SRC}
)
target_link_libraries(trace_proto PRIVATE protobuf::libprotobuf)
