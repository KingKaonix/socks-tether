cmake_minimum_required(VERSION 3.22.1)
project("sockstether")

# Find required packages
find_library(log-lib log)

# Add tun2socks library (will be downloaded as submodule)
# For now, create a minimal native library
add_library(sockstether SHARED
    tun2socks_jni.c
)

target_link_libraries(sockstether
    ${log-lib}
)

# Include directories
target_include_directories(sockstether PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
)
