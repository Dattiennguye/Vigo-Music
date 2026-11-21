cmake_minimum_required(VERSION 3.22.1)
project(vigowhisper C CXX)

# whisper.cpp (submodule, cố định v1.9.5) — nhận dạng giọng hát chạy hoàn toàn trên máy
set(WHISPER_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/whisper.cpp)

set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(WHISPER_ALL_WARNINGS OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
# Snapdragon 8 Gen 2/3 (Xiaomi 13/14) hỗ trợ fp16 + dotprod
set(GGML_CPU_ARM_ARCH "armv8.2-a+fp16+dotprod" CACHE STRING "" FORCE)

add_subdirectory(${WHISPER_DIR} whisper_build)

add_library(vigowhisper SHARED vigo_whisper.c)
target_link_libraries(vigowhisper whisper log android)
target_compile_options(vigowhisper PRIVATE -O3)
