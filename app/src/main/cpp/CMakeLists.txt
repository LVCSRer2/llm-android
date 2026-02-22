cmake_minimum_required(VERSION 3.18)
project(llama-jni)

set(LLAMA_CPP_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../../llama.cpp)

# Build llama.cpp as a static library
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
# Vulkan GPU acceleration (disabled - causes SIGSEGV on some devices during backend init)
# set(GGML_VULKAN ON CACHE BOOL "" FORCE)
# set(GGML_VULKAN_SHADERS_GEN_TOOLCHAIN ${CMAKE_CURRENT_SOURCE_DIR}/host-toolchain.cmake CACHE FILEPATH "" FORCE)
# set(VULKAN_HEADERS_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../../external/Vulkan-Headers/include)
# include_directories(BEFORE ${VULKAN_HEADERS_DIR})
# set(Vulkan_GLSLC_EXECUTABLE /home/jieunstage/android-sdk/ndk/25.1.8937393/shader-tools/linux-x86_64/glslc CACHE FILEPATH "" FORCE)

add_subdirectory(${LLAMA_CPP_DIR} llama.cpp)

# JNI bridge
add_library(llama-jni SHARED llama_jni.cpp)

target_include_directories(llama-jni PRIVATE
    ${LLAMA_CPP_DIR}/include
)

find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(llama-jni
    llama
    ${log-lib}
    ${android-lib}
)
