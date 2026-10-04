static std::optional<std::wstring> GetProcessCommandLine(DWORD pid) {
    if (pid == 0) {
        return std::nullopt;
    }
    std::wstring query = L"$OutputEncoding=[Console]::OutputEncoding=[Text.UTF8Encoding]::UTF8; "
            L"(Get-CimInstance Win32_Process -Filter \"ProcessId="
            + std::to_wstring(pid) + L"\").CommandLine";
    std::string output = RunPowerShell(query);
    if (output.empty()) {
        return std::nullopt;
    }
    while (!output.empty() && (output.back() == '\r' || output.back() == '\n' || output.back() == ' ')) {
        output.pop_back();
    }
    if (output.empty()) {
        return std::nullopt;
    }
    int needed = MultiByteToWideChar(CP_UTF8, 0, output.c_str(), -1, nullptr, 0);
    if (needed <= 1) {
        return Widen(output);
    }
    std::wstring wide(static_cast<size_t>(needed - 1), L'\0');
    MultiByteToWideChar(CP_UTF8, 0, output.c_str(), -1, wide.data(), needed);
    return wide;
}

std::optional<std::wstring> DetectRunningMinecraftVersion(DWORD pid) {
    auto commandLine = GetProcessCommandLine(pid);
    if (!commandLine || commandLine->empty()) {
        return std::nullopt;
    }
    auto extracted = ExtractVersionFromText(ToLowerA(Narrow(*commandLine)));
    if (extracted && !extracted->empty()) {
        return Widen(*extracted);
    }
    std::wstring lower = ToLowerW(*commandLine);
    if (lower.find(L"26.5") != std::wstring::npos) {
        return L"26.5";
    }
    if (lower.find(L"26.4") != std::wstring::npos) {
        return L"26.4";
    }
    if (lower.find(L"26.3") != std::wstring::npos) {
        return L"26.3";
    }
    if (lower.find(L"26.2") != std::wstring::npos) {
        return L"26.2";
    }
    if (lower.find(L"26.1.2") != std::wstring::npos) {
        return L"26.1.2";
    }
    if (lower.find(L"26.1") != std::wstring::npos) {
        return L"26.1";
    }
    if (lower.find(L"1.21.11") != std::wstring::npos) {
        return L"1.21.11";
    }
    if (lower.find(L"1.21.4") != std::wstring::npos) {
        return L"1.21.4";
    }
    return std::nullopt;
}

std::wstring DetectRunningLoaderLabel(DWORD pid) {
    auto commandLine = GetProcessCommandLine(pid);
    if (!commandLine || commandLine->empty()) {
        return L"";
    }
    std::wstring lower = ToLowerW(*commandLine);
    if (lower.find(L"net.fabricmc.loader") != std::wstring::npos
            || lower.find(L"fabric-loader") != std::wstring::npos) {
        return L"Fabric";
    }
    if (lower.find(L"forge") != std::wstring::npos || lower.find(L"neoforge") != std::wstring::npos) {
        return L"Forge";
    }
    if (lower.find(L"net.minecraft.client.main") != std::wstring::npos) {
        return L"Vanilla";
    }
    return L"";
}

static std::optional<std::wstring> ExtractGameDirFromCommandLine(const std::wstring& cmd) {
    auto extractValue = [&](size_t keyPos, size_t keyLen) -> std::optional<std::wstring> {
        size_t after = keyPos + keyLen;
        if (after < cmd.size() && cmd[after] == L'=') {
            ++after;
            if (after < cmd.size() && cmd[after] == L'"') {
                ++after;
                size_t end = cmd.find(L'"', after);
                if (end == std::wstring::npos || end <= after) {
                    return std::nullopt;
                }
                return cmd.substr(after, end - after);
            }
            size_t end = after;
            while (end < cmd.size() && cmd[end] != L' ' && cmd[end] != L'\t') {
                ++end;
            }
            if (end <= after) {
                return std::nullopt;
            }
            return cmd.substr(after, end - after);
        }
        if (after >= cmd.size() || (cmd[after] != L' ' && cmd[after] != L'\t')) {
            return std::nullopt;
        }
        size_t start = after;
        while (start < cmd.size() && (cmd[start] == L' ' || cmd[start] == L'\t')) {
            ++start;
        }
        if (start >= cmd.size()) {
            return std::nullopt;
        }
        if (cmd[start] == L'"') {
            ++start;
            size_t end = cmd.find(L'"', start);
            if (end == std::wstring::npos || end <= start) {
                return std::nullopt;
            }
            return cmd.substr(start, end - start);
        }
        size_t end = start;
        while (end < cmd.size() && cmd[end] != L' ' && cmd[end] != L'\t') {
            ++end;
        }
        if (end <= start) {
            return std::nullopt;
        }
        return cmd.substr(start, end - start);
    };

    std::wstring lower = ToLowerW(cmd);
    const wchar_t* keys[] = {L"--gamedir", L"--game-dir"};
    for (const wchar_t* key : keys) {
        std::wstring keyLower = ToLowerW(std::wstring(key));
        size_t pos = 0;
        while ((pos = lower.find(keyLower, pos)) != std::wstring::npos) {
            if (pos == 0 || cmd[pos - 1] == L' ' || cmd[pos - 1] == L'\t') {
                if (auto value = extractValue(pos, keyLower.size())) {
                    return value;
                }
            }
            pos += keyLower.size();
        }
    }
    return std::nullopt;
}

static std::optional<std::wstring> ResolveGameDirForPid(DWORD pid) {
    if (pid != 0) {
        auto commandLine = GetProcessCommandLine(pid);
        if (commandLine) {
            auto gameDir = ExtractGameDirFromCommandLine(*commandLine);
            if (gameDir && !gameDir->empty() && fs::exists(*gameDir)) {
                return gameDir;
            }
        }
    }
    auto instance = FindPreferredInstancePath(L"");
    if (instance && !instance->empty()) {
        return instance;
    }
    return std::nullopt;
}

static std::optional<std::string> ReadLogTailUtf8(const fs::path& path, size_t maxBytes = 131072) {
    HANDLE file = CreateFileW(path.c_str(), GENERIC_READ,
            FILE_SHARE_READ | FILE_SHARE_WRITE | FILE_SHARE_DELETE,
            nullptr, OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, nullptr);
    if (file == INVALID_HANDLE_VALUE) {
        return std::nullopt;
    }
    LARGE_INTEGER fileSize{};
    if (!GetFileSizeEx(file, &fileSize) || fileSize.QuadPart <= 0) {
        CloseHandle(file);
        return std::nullopt;
    }
    DWORD toRead = static_cast<DWORD>(std::min<size_t>(static_cast<size_t>(fileSize.QuadPart), maxBytes));
    LARGE_INTEGER offset{};
    offset.QuadPart = fileSize.QuadPart - toRead;
    SetFilePointerEx(file, offset, nullptr, FILE_BEGIN);
    std::string body(toRead, '\0');
    DWORD read = 0;
    if (!ReadFile(file, body.data(), toRead, &read, nullptr) || read == 0) {
        CloseHandle(file);
        return std::nullopt;
    }
    CloseHandle(file);
    body.resize(read);
    return body;
}

static bool LogTailShowsInWorld(const std::string& logTail) {
    if (logTail.empty()) {
        return false;
    }
    std::string lower = logTail;
    std::transform(lower.begin(), lower.end(), lower.begin(),
            [](unsigned char c) { return static_cast<char>(std::tolower(c)); });
    static const char* markers[] = {
            "joining world",
            "joined world",
            "started integrated minecraft server",
            "entering dimension",
            "preparing spawn area",
            "handling connection to ",
            "loaded ",
            "changing view distance",
            "time elapsed:",
            "logged in with entity id"
    };
    for (const char* marker : markers) {
        if (lower.find(marker) != std::string::npos) {
            if (std::strcmp(marker, "loaded ") == 0) {
                if (lower.find("loaded ") != std::string::npos
                        && (lower.find(" chunks") != std::string::npos
                                || lower.find(" advancements") != std::string::npos
                                || lower.find(" entities") != std::string::npos)) {
                    return true;
                }
                continue;
            }
            return true;
        }
    }
    return false;
}

static bool ReadInstanceLogShowsInWorld(const std::wstring& instancePath) {
    if (instancePath.empty()) {
        return false;
    }
    fs::path logPath = fs::path(instancePath) / L"logs" / L"latest.log";
    fs::path altLog = fs::path(instancePath) / L".minecraft" / L"logs" / L"latest.log";
    auto content = ReadLogTailUtf8(logPath);
    if (!content) {
        content = ReadLogTailUtf8(altLog);
    }
    if (!content) {
        return false;
    }
    return LogTailShowsInWorld(*content);
}

bool IsMinecraftInWorldForInstance(const std::wstring& instancePath) {
    return ReadInstanceLogShowsInWorld(instancePath);
}

bool IsMinecraftInWorld(DWORD pid) {
    auto gameDir = ResolveGameDirForPid(pid);
    if (!gameDir) {
        return false;
    }
    return ReadInstanceLogShowsInWorld(*gameDir);
}

static HANDLE g_vapeServiceProcess = nullptr;
