cmake_minimum_required(VERSION 3.22.1)
project(dailyreader LANGUAGES CXX)

add_library(node SHARED IMPORTED)
set_target_properties(node PROPERTIES IMPORTED_LOCATION "${NODE_RUNTIME}/lib/libnode.so")

add_library(dailyreader SHARED launch.cpp)
target_compile_features(dailyreader PRIVATE cxx_std_20)
target_include_directories(dailyreader PRIVATE "${NODE_RUNTIME}/include/node")
target_link_libraries(dailyreader PRIVATE node)
