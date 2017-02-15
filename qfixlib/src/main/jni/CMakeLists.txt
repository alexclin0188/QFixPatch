cmake_minimum_required(VERSION 3.4.1)

add_library(ResolvePatch SHARED
            ResolvePatch.c)

target_link_libraries(ResolvePatch
                        android
                        log)