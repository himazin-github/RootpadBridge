cmake_minimum_required(VERSION 3.22.1)
project(rootpad C)

add_compile_options(-Wall -Wextra -O2 -fno-strict-aliasing)

# rootpad_d: root 権限で実行する入力ブリッジデーモン (JNI ではなく単体実行ファイル)
add_executable(rootpad_d
    daemon_main.c
    uinput_pad.c
    input_dev.c
    usock.c)

target_include_directories(rootpad_d PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})

# ビルド完了後に APK の assets へコピーする (アプリが抽出して root 起動する)
add_custom_command(TARGET rootpad_d POST_BUILD
    COMMAND ${CMAKE_COMMAND} -E make_directory ${CMAKE_CURRENT_SOURCE_DIR}/../assetsDaemon
    COMMAND ${CMAKE_COMMAND} -E copy $<TARGET_FILE:rootpad_d> ${CMAKE_CURRENT_SOURCE_DIR}/../assetsDaemon/rootpad_d
    COMMENT "Copying rootpad_d into assetsDaemon for APK packaging")
