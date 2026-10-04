#include "inject_core.h"

#include <algorithm>
#include <iostream>
#include <string>
#include <filesystem>
#include <shellapi.h>

#pragma comment(lib, "shell32.lib")
#pragma comment(lib, "advapi32.lib")
#pragma comment(lib, "user32.lib")

namespace fs = std::filesystem;

#if defined(GHOST_BRAND_DOOMSDAY)
#  ifndef GHOST_RUNTIME_FOLDER
#    define GHOST_RUNTIME_FOLDER L"DoomsdayInject"
#  endif
#  ifndef GHOST_AGENT_CLASS
#    define GHOST_AGENT_CLASS "nv.navineghost.injector.GhostAgent"
#  endif
#  ifndef GHOST_ATTACHER_CLASS
#    define GHOST_ATTACHER_CLASS "nv.navineghost.injector.AgentAttacher"
#  endif
#  ifndef GHOST_INJECT_LOG
#    define GHOST_INJECT_LOG L"doomsday-java-inject.log"
#  endif
#elif defined(GHOST_BRAND_NAVURYX)
#  ifndef GHOST_RUNTIME_FOLDER
#    define GHOST_RUNTIME_FOLDER L"NavuryxGhost"
#  endif
#  ifndef GHOST_AGENT_CLASS
#    define GHOST_AGENT_CLASS "nv.navuryxghost.injector.GhostAgent"
#  endif
#  ifndef GHOST_ATTACHER_CLASS
#    define GHOST_ATTACHER_CLASS "nv.navuryxghost.injector.AgentAttacher"
#  endif
#  ifndef GHOST_INJECT_LOG
#    define GHOST_INJECT_LOG L"ghost-java-inject.log"
#  endif
#elif defined(GHOST_BRAND_PRESTIGE)
#  ifndef GHOST_RUNTIME_FOLDER
#    define GHOST_RUNTIME_FOLDER L"PrestigeInject"
#  endif
#  ifndef GHOST_AGENT_CLASS
#    define GHOST_AGENT_CLASS "nv.navineghost.injector.GhostAgent"
#  endif
#  ifndef GHOST_ATTACHER_CLASS
#    define GHOST_ATTACHER_CLASS "nv.navineghost.injector.AgentAttacher"
#  endif
#  ifndef GHOST_INJECT_LOG
#    define GHOST_INJECT_LOG L"prestige-java-inject.log"
#  endif
#else
#  ifndef GHOST_AGENT_CLASS
#    define GHOST_AGENT_CLASS "nv.navineghost.injector.GhostAgent"
#  endif
#  ifndef GHOST_ATTACHER_CLASS
#    define GHOST_ATTACHER_CLASS "nv.navineghost.injector.AgentAttacher"
#  endif
#  ifndef GHOST_RUNTIME_FOLDER
#    define GHOST_RUNTIME_FOLDER L"NavineGhost"
#  endif
#  ifndef GHOST_INJECT_LOG
#    define GHOST_INJECT_LOG L"ghost-java-inject.log"
#  endif
#endif

static std::wstring ArgValue(int argc, wchar_t** argv, const wchar_t* key) {
    std::wstring prefix = std::wstring(key) + L"=";
    for (int i = 1; i < argc; i++) {
        std::wstring a = argv[i];
        if (a.rfind(prefix, 0) == 0) {
            return a.substr(prefix.size());
        }
        if (a == key && i + 1 < argc) {
            return argv[i + 1];
        }
    }
    return L"";
}

static bool HasFlag(int argc, wchar_t** argv, const wchar_t* flag) {
    for (int i = 1; i < argc; i++) {
        if (argv[i] == std::wstring(flag)) {
            return true;
        }
    }
    return false;
}

static std::wstring NormalizeLoader(const std::wstring& loader) {
    std::wstring lower = loader;
    std::transform(lower.begin(), lower.end(), lower.begin(), ::towlower);
    if (lower == L"fabric") {
        return L"fabric";
    }
    if (lower == L"vanilla" || lower == L"forge" || lower == L"vanilla / forge") {
        return L"vanilla";
    }
    return L"";
}

static void ApplyLoaderEnv(const std::wstring& loader) {
    if (!loader.empty()) {
        SetEnvironmentVariableW(L"GHOST_FORCE_LOADER", loader.c_str());
    } else {
        SetEnvironmentVariableW(L"GHOST_FORCE_LOADER", nullptr);
    }
}

static ghost::RuntimePaths BuildPaths() {
    ghost::RuntimePaths paths;
    static std::wstring session = std::to_wstring(GetTickCount64());
    paths.root = ghost::LocalAppData() + L"\\" + GHOST_RUNTIME_FOLDER + L"\\runtime\\" + session;
    paths.agent = paths.root + L"\\agent.jar";
    paths.payload = paths.root + L"\\payload.jar";
    paths.payloadVanilla = paths.root + L"\\payload-vanilla.jar";
    paths.dll = paths.root + L"\\bridge.dll";
    paths.cfg = ghost::TempPath() + L"ghost-java-inject.cfg";
    ghost::EnsureDir(paths.root);
    return paths;
}

static bool CopyResourceFile(const fs::path& resourceDir, const wchar_t* name, const std::wstring& dest) {
    fs::path src = resourceDir / name;
    if (!fs::exists(src)) {
        return false;
    }
    return CopyFileW(src.wstring().c_str(), dest.c_str(), FALSE) != 0;
}

static bool PrepareResources(const std::wstring& resourceDir, ghost::RuntimePaths& paths, std::wstring& error) {
    fs::path root = resourceDir.empty() ? fs::path(ghost::GetExeDir()) : fs::path(resourceDir);
    if (!CopyResourceFile(root, L"agent.jar", paths.agent)) {
        error = L"Missing agent.jar in resources.";
        return false;
    }
    if (!CopyResourceFile(root, L"payload.jar", paths.payload)) {
        CopyFileW(paths.agent.c_str(), paths.payload.c_str(), FALSE);
    }
    if (!CopyResourceFile(root, L"payload-vanilla.jar", paths.payloadVanilla)) {
        CopyFileW(paths.payload.c_str(), paths.payloadVanilla.c_str(), FALSE);
    }
    if (!CopyResourceFile(root, L"ghost_java_inject.dll", paths.dll)
            && !CopyResourceFile(root, L"bridge.dll", paths.dll)
            && !CopyResourceFile(root, L"runtime-bridge.dll", paths.dll)) {
        error = L"Missing ghost_java_inject.dll in resources.";
        return false;
    }
    if (!fs::exists(paths.agent) || !fs::exists(paths.payload) || !fs::exists(paths.dll)) {
        error = L"Incomplete inject resources.";
        return false;
    }
    return true;
}

static std::string JsonEscape(const std::string& value) {
    std::string out;
    out.reserve(value.size() + 8);
    for (char c : value) {
        if (c == '\\' || c == '"') {
            out.push_back('\\');
        }
        out.push_back(c);
    }
    return out;
}

static int RunStatus() {
    auto pid = ghost::FindMinecraftJavaPid();
    if (!pid) {
        std::cout << "{\"live\":false,\"version\":\"\",\"loader\":\"\"}\n";
        return 0;
    }
    std::wstring version = ghost::DetectRunningMinecraftVersion(*pid).value_or(L"");
    std::wstring loader = ghost::DetectRunningLoaderLabel(*pid);
    std::cout << "{\"live\":true,\"version\":\"" << JsonEscape(ghost::Narrow(version))
            << "\",\"loader\":\"" << JsonEscape(ghost::Narrow(loader)) << "\"}\n";
    return 0;
}

static bool InjectSucceeded() {
    auto logPath = ghost::TempPath() + GHOST_INJECT_LOG;
    HANDLE h = CreateFileW(logPath.c_str(), GENERIC_READ, FILE_SHARE_READ | FILE_SHARE_WRITE,
            nullptr, OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, nullptr);
    if (h == INVALID_HANDLE_VALUE) {
        return false;
    }
    DWORD size = GetFileSize(h, nullptr);
    if (size == 0 || size >= 4 * 1024 * 1024) {
        CloseHandle(h);
        return false;
    }
    std::string body(size, '\0');
    DWORD read = 0;
    if (!ReadFile(h, body.data(), size, &read, nullptr)) {
        CloseHandle(h);
        return false;
    }
    CloseHandle(h);
    body.resize(read);
    bool clientUp = body.find("clientStarted=true") != std::string::npos
            || body.find("client bootstrapped") != std::string::npos;
    bool tickFired = body.find("tickFired=true") != std::string::npos
            || body.find("ghost done") != std::string::npos;
    return clientUp && tickFired;
}

static int RunInject(const std::wstring& version, const std::wstring& resourceDir,
        const std::string& agentClass, const std::wstring& loaderForce) {
    ghost::RuntimePaths paths = BuildPaths();
    std::wstring error;
    if (!PrepareResources(resourceDir, paths, error)) {
        std::wcerr << error << L"\n";
        return 2;
    }
    if (!ghost::WriteInjectConfig(paths, agentClass, ghost::Narrow(loaderForce))) {
        return 3;
    }

    auto instance = ghost::FindPreferredInstancePath(version);
    if (!instance) {
        instance = ghost::FindPreferredInstancePath(L"");
    }
    if (instance) {
        ghost::RemoveGhostInstallArtifacts(*instance);
    }

    ghost::EnableDebugPrivilege();
    ApplyLoaderEnv(loaderForce);
    DeleteFileW((ghost::TempPath() + GHOST_INJECT_LOG).c_str());

    auto pid = ghost::FindMinecraftJavaPid();
    if (!pid) {
        return 4;
    }

    bool attached = ghost::AttachJavaAgent(*pid, paths, GHOST_ATTACHER_CLASS);
    bool dllOk = ghost::InjectDllWithRetries(*pid, paths.dll, 8, 350);
    if (!dllOk) {
        auto again = ghost::FindMinecraftJavaPid();
        if (again) {
            dllOk = ghost::InjectDllWithRetries(*again, paths.dll, 6, 400);
            if (!attached) {
                attached = ghost::AttachJavaAgent(*again, paths, GHOST_ATTACHER_CLASS);
            }
        }
    }
    (void)attached;
    if (!dllOk) {
        return 5;
    }

    ghost::WaitForInjectEvidence(instance, 20000);
    DWORD waitStart = GetTickCount();
    while (GetTickCount() - waitStart < 12000) {
        if (InjectSucceeded()) {
            return 0;
        }
        Sleep(200);
    }
    return InjectSucceeded() ? 0 : 5;
}

int wmain(int argc, wchar_t** argv) {
    if (HasFlag(argc, argv, L"--status")) {
        return RunStatus();
    }

    std::wstring version = ArgValue(argc, argv, L"--version");
    if (version.empty()) {
        version = L"26.1.2";
    }
    std::wstring resourceDir = ArgValue(argc, argv, L"--resources");
    std::wstring agentClassW = ArgValue(argc, argv, L"--agent-class");
    std::wstring loader = NormalizeLoader(ArgValue(argc, argv, L"--loader"));
    std::string agentClass = agentClassW.empty() ? std::string(GHOST_AGENT_CLASS) : ghost::Narrow(agentClassW);

    return RunInject(version, resourceDir, agentClass, loader);
}
