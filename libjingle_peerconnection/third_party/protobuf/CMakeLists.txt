cmake_minimum_required(VERSION 3.4.1)

add_definitions(-DHAVE_PTHREAD)

include_directories(
    ${WEBRTC_REPO}/third_party/protobuf/src
)

set(src_protobuf_lite
    ${WEBRTC_REPO}/third_party/protobuf/src/google/protobuf/arena.cc
    ${WEBRTC_REPO}/third_party/protobuf/src/google/protobuf/arenastring.cc
    ${WEBRTC_REPO}/third_party/protobuf/src/google/protobuf/extension_set.cc
    ${WEBRTC_REPO}/third_party/protobuf/src/google/protobuf/generated_message_util.cc
    ${WEBRTC_REPO}/third_party/protobuf/src/google/protobuf/io/coded_stream.cc
    ${WEBRTC_REPO}/third_party/protobuf/src/google/protobuf/io/zero_copy_stream.cc
    ${WEBRTC_REPO}/third_party/protobuf/src/google/protobuf/io/zero_copy_stream_impl_lite.cc
    ${WEBRTC_REPO}/third_party/protobuf/src/google/protobuf/message_lite.cc
    ${WEBRTC_REPO}/third_party/protobuf/src/google/protobuf/repeated_field.cc
    ${WEBRTC_REPO}/third_party/protobuf/src/google/protobuf/stubs/atomicops_internals_x86_gcc.cc
    ${WEBRTC_REPO}/third_party/protobuf/src/google/protobuf/stubs/atomicops_internals_x86_msvc.cc
    ${WEBRTC_REPO}/third_party/protobuf/src/google/protobuf/stubs/bytestream.cc
    ${WEBRTC_REPO}/third_party/protobuf/src/google/protobuf/stubs/common.cc
    ${WEBRTC_REPO}/third_party/protobuf/src/google/protobuf/stubs/int128.cc
    ${WEBRTC_REPO}/third_party/protobuf/src/google/protobuf/stubs/once.cc
    ${WEBRTC_REPO}/third_party/protobuf/src/google/protobuf/stubs/status.cc
    ${WEBRTC_REPO}/third_party/protobuf/src/google/protobuf/stubs/statusor.cc
    ${WEBRTC_REPO}/third_party/protobuf/src/google/protobuf/stubs/stringpiece.cc
    ${WEBRTC_REPO}/third_party/protobuf/src/google/protobuf/stubs/stringprintf.cc
    ${WEBRTC_REPO}/third_party/protobuf/src/google/protobuf/stubs/structurally_valid.cc
    ${WEBRTC_REPO}/third_party/protobuf/src/google/protobuf/stubs/strutil.cc
    ${WEBRTC_REPO}/third_party/protobuf/src/google/protobuf/stubs/time.cc
    ${WEBRTC_REPO}/third_party/protobuf/src/google/protobuf/wire_format_lite.cc
)

add_library(protobuf_lite STATIC
    ${src_protobuf_lite}
)
