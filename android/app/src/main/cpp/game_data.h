#pragma once
#include <cstdio>
#include <dirent.h>
#include <sys/stat.h>
#include <string>
#include <stdexcept>
#include <android/log.h>
#include <algorithm>
#include <vector>
#include <cerrno>

inline bool hasGameData(const std::string& root) {
    FILE* exe = std::fopen((root + "/SPEED2.EXE").c_str(), "rb");
    if (!exe) {
        __android_log_print(ANDROID_LOG_INFO, "NFSU2", "Game data candidate %s: SPEED2.EXE unavailable (errno=%d)",
                            root.c_str(), errno);
        return false;
    }
    unsigned char magic[2]{};
    const size_t read = std::fread(magic, 1, 2, exe);
    std::fclose(exe);
    struct stat info{};
    if (read != 2 || magic[0] != 'M' || magic[1] != 'Z' ||
        stat((root + "/SPEED2.EXE").c_str(), &info) || info.st_size != 4800512) {
        __android_log_print(ANDROID_LOG_WARN, "NFSU2", "Game data candidate %s: SPEED2.EXE header/size invalid",
                            root.c_str());
        return false;
    }
    for (const char* name : {"CARS", "CREDITS", "FRONTEND", "GLOBAL", "LANGUAGES", "MOVIES",
                             "NIS", "SDATA", "SOUND", "SUBTITLES", "TRACKS"}) {
        const auto path = root + "/" + name;
        DIR* dir = opendir(path.c_str());
        if (!dir) {
            __android_log_print(ANDROID_LOG_WARN, "NFSU2", "Game data candidate %s: cannot read %s (errno=%d)",
                                root.c_str(), name, errno);
            return false;
        }
        unsigned files = 0;
        while (auto* entry = readdir(dir)) if (entry->d_name[0] != '.') ++files;
        closedir(dir);
        if (!files) {
            __android_log_print(ANDROID_LOG_WARN, "NFSU2", "Game data candidate %s: directory %s is empty",
                                root.c_str(), name);
            return false;
        }
        __android_log_print(ANDROID_LOG_INFO, "NFSU2", "Data directory %s: %u entries", name, files);
    }
    return true;
}

inline std::string findGameDataRoot(const std::string& preferredRoot = {}) {
    std::vector<std::string> roots{"/storage/emulated/0/nfsu2"};
    if (!preferredRoot.empty() && preferredRoot.front() == '/') {
        roots.insert(roots.begin(), preferredRoot);
        __android_log_print(ANDROID_LOG_INFO, "NFSU2", "Launcher selected game data path: %s", preferredRoot.c_str());
    }
    DIR* storage = opendir("/storage");
    if (storage) {
        while (auto* entry = readdir(storage)) {
            if (entry->d_name[0] == '.' || std::string(entry->d_name) == "emulated") continue;
            roots.emplace_back(std::string("/storage/") + entry->d_name + "/nfsu2");
        }
        closedir(storage);
    }
    std::sort(roots.begin() + 1, roots.end());
    roots.erase(std::unique(roots.begin(), roots.end()), roots.end());
    for (const auto& root : roots) {
        if (hasGameData(root)) {
            __android_log_print(ANDROID_LOG_INFO, "NFSU2", "Game data readable at %s; executable header/size OK (guest not running)", root.c_str());
            return root;
        }
    }
    throw std::runtime_error("Cannot find valid game files in storage/nfsu2 or /storage/<volume>/nfsu2; check files and storage permission");
}
