#include "inject_core.h"

#include <tlhelp32.h>
#include <psapi.h>
#include <fstream>
#include <sstream>
#include <filesystem>
#include <algorithm>
#include <regex>
#include <cctype>
#include <vector>
#include <cstring>

#pragma comment(lib, "psapi.lib")

namespace fs = std::filesystem;

namespace ghost {

static std::optional<std::string> ReadFileUtf8(const fs::path& path);

std::wstring LocalAppData() {
    wchar_t buf[MAX_PATH] = {};
    DWORD n = GetEnvironmentVariableW(L"LOCALAPPDATA", buf, MAX_PATH);
    if (n == 0 || n >= MAX_PATH) {
        return TempPath();
    }
    return buf;
}

std::wstring AppData() {
    wchar_t buf[MAX_PATH] = {};
    DWORD n = GetEnvironmentVariableW(L"APPDATA", buf, MAX_PATH);
    if (n == 0 || n >= MAX_PATH) {
        return LocalAppData();
    }
    return buf;
}

std::wstring TempPath() {
    wchar_t buf[MAX_PATH] = {};
    GetTempPathW(MAX_PATH, buf);
    return buf;
}

bool EnsureDir(const std::wstring& path) {
    std::error_code ec;
    fs::create_directories(path, ec);
    return !ec;
}

void AppendNativeInjectLog(const std::string& line) {
    try {
        std::ofstream out(fs::path(TempPath()) / L"ghost-native-inject.log", std::ios::app);
        if (out) {
            out << line << '\n';
        }
    } catch (...) {
    }
}

bool WriteFileBytes(const std::wstring& path, const void* data, size_t size) {
    // Force replace locked/stale files by writing a temp then replacing.
    fs::path target(path);
    EnsureDir(target.parent_path().wstring());
    std::wstring temp = path + L".tmp-" + std::to_wstring(GetTickCount64());
    HANDLE file = CreateFileW(temp.c_str(), GENERIC_WRITE, 0, nullptr, CREATE_ALWAYS, FILE_ATTRIBUTE_NORMAL, nullptr);
    if (file == INVALID_HANDLE_VALUE) {
        file = CreateFileW(path.c_str(), GENERIC_WRITE, 0, nullptr, CREATE_ALWAYS, FILE_ATTRIBUTE_NORMAL, nullptr);
        if (file == INVALID_HANDLE_VALUE) {
            return false;
        }
        DWORD written = 0;
        BOOL ok = WriteFile(file, data, static_cast<DWORD>(size), &written, nullptr);
        CloseHandle(file);
        return ok && written == size;
    }
    DWORD written = 0;
    BOOL ok = WriteFile(file, data, static_cast<DWORD>(size), &written, nullptr);
    CloseHandle(file);
    if (!ok || written != size) {
        DeleteFileW(temp.c_str());
        return false;
    }
    SetFileAttributesW(path.c_str(), FILE_ATTRIBUTE_NORMAL);
    DeleteFileW(path.c_str());
    if (!MoveFileExW(temp.c_str(), path.c_str(), MOVEFILE_REPLACE_EXISTING | MOVEFILE_COPY_ALLOWED)) {
        // Last resort: copy over
        BOOL copied = CopyFileW(temp.c_str(), path.c_str(), FALSE);
        DeleteFileW(temp.c_str());
        return copied != 0;
    }
    return true;
}

bool ExtractResource(HMODULE module, int resId, const std::wstring& outPath) {
    HRSRC res = FindResourceW(module, MAKEINTRESOURCEW(resId), RT_RCDATA);
    if (!res) {
        return false;
    }
    HGLOBAL loaded = LoadResource(module, res);
    if (!loaded) {
        return false;
    }
    DWORD size = SizeofResource(module, res);
    void* data = LockResource(loaded);
    if (!data || size == 0) {
        return false;
    }
    fs::path parent = fs::path(outPath).parent_path();
    EnsureDir(parent.wstring());
    return WriteFileBytes(outPath, data, size);
}

std::string Narrow(const std::wstring& wide) {
    if (wide.empty()) {
        return {};
    }
    int needed = WideCharToMultiByte(CP_UTF8, 0, wide.c_str(), -1, nullptr, 0, nullptr, nullptr);
    std::string out(static_cast<size_t>(needed - 1), '\0');
    WideCharToMultiByte(CP_UTF8, 0, wide.c_str(), -1, out.data(), needed, nullptr, nullptr);
    return out;
}

std::wstring Widen(const std::string& utf8) {
    if (utf8.empty()) {
        return {};
    }
    int needed = MultiByteToWideChar(CP_UTF8, 0, utf8.c_str(), -1, nullptr, 0);
    std::wstring out(static_cast<size_t>(needed - 1), L'\0');
    MultiByteToWideChar(CP_UTF8, 0, utf8.c_str(), -1, out.data(), needed);
    return out;
}

std::string EscapeJson(const std::string& value) {
    std::string out;
    out.reserve(value.size() + 8);
    for (char ch : value) {
        if (ch == '\\' || ch == '"') {
            out.push_back('\\');
        }
        out.push_back(ch);
    }
    return out;
}

bool WriteInjectConfig(const RuntimePaths& paths, const std::string& agentClass) {
    return WriteInjectConfig(paths, agentClass, "");
}

bool WriteInjectConfig(const RuntimePaths& paths, const std::string& agentClass, const std::string& forceLoader) {
    // agent, fabric-payload, agentClass, vanilla-payload, forceLoader (fabric|vanilla|empty)
    std::string vanilla = paths.payloadVanilla.empty() ? Narrow(paths.payload) : Narrow(paths.payloadVanilla);
    std::string body = Narrow(paths.agent) + "\n" + Narrow(paths.payload) + "\n" + agentClass + "\n" + vanilla + "\n";
    if (!forceLoader.empty()) {
        body += forceLoader;
    }
    body += "\n";
    return WriteFileBytes(paths.cfg, body.data(), body.size());
}

bool EnableDebugPrivilege() {
    HANDLE token = nullptr;
    if (!OpenProcessToken(GetCurrentProcess(), TOKEN_ADJUST_PRIVILEGES | TOKEN_QUERY, &token)) {
        return false;
    }
    TOKEN_PRIVILEGES tp{};
    LUID luid{};
    if (!LookupPrivilegeValueW(nullptr, L"SeDebugPrivilege", &luid)) {
        CloseHandle(token);
        return false;
    }
    tp.PrivilegeCount = 1;
    tp.Privileges[0].Luid = luid;
    tp.Privileges[0].Attributes = SE_PRIVILEGE_ENABLED;
    bool ok = AdjustTokenPrivileges(token, FALSE, &tp, sizeof(tp), nullptr, nullptr) != FALSE;
    CloseHandle(token);
    return ok;
}

static HMODULE FindRemoteModule(HANDLE process, const std::wstring& pathHint) {
    HMODULE modules[1024];
    DWORD needed = 0;
    if (!EnumProcessModulesEx(process, modules, sizeof(modules), &needed, LIST_MODULES_ALL)) {
        if (!EnumProcessModules(process, modules, sizeof(modules), &needed)) {
            return nullptr;
        }
    }
    std::wstring needle = pathHint;
    std::transform(needle.begin(), needle.end(), needle.begin(), ::towlower);
    auto base = needle.find_last_of(L"\\/");
    std::wstring file = base == std::wstring::npos ? needle : needle.substr(base + 1);

    const DWORD count = needed / sizeof(HMODULE);
    for (DWORD i = 0; i < count; i++) {
        wchar_t name[MAX_PATH] = {};
        if (GetModuleFileNameExW(process, modules[i], name, MAX_PATH) == 0) {
            continue;
        }
        std::wstring lower = name;
        std::transform(lower.begin(), lower.end(), lower.begin(), ::towlower);
        if (lower == needle || (!file.empty() && lower.size() >= file.size()
                && lower.compare(lower.size() - file.size(), file.size(), file) == 0)) {
            return modules[i];
        }
    }
    return nullptr;
}

bool InjectDll(DWORD pid, const std::wstring& dllPath) {
    if (pid == 0 || dllPath.empty() || GetFileAttributesW(dllPath.c_str()) == INVALID_FILE_ATTRIBUTES) {
        return false;
    }

    std::wstring injectPath = dllPath;
    fs::path source(dllPath);
    if (!source.empty()) {
        fs::path staged = source.parent_path()
                / (L"nvshim-" + std::to_wstring(GetTickCount64()) + L".dll");
        if (CopyFileW(dllPath.c_str(), staged.wstring().c_str(), FALSE)
                && GetFileAttributesW(staged.wstring().c_str()) != INVALID_FILE_ATTRIBUTES) {
            injectPath = staged.wstring();
        }
    }
    wchar_t shortPath[MAX_PATH] = {};
    DWORD sn = GetShortPathNameW(injectPath.c_str(), shortPath, MAX_PATH);
    if (sn > 0 && sn < MAX_PATH) {
        injectPath = shortPath;
    }

    HANDLE process = OpenProcess(PROCESS_CREATE_THREAD | PROCESS_QUERY_INFORMATION | PROCESS_VM_OPERATION
            | PROCESS_VM_WRITE | PROCESS_VM_READ | PROCESS_QUERY_LIMITED_INFORMATION, FALSE, pid);
    if (!process) {
        process = OpenProcess(PROCESS_CREATE_THREAD | PROCESS_QUERY_INFORMATION | PROCESS_VM_OPERATION
                | PROCESS_VM_WRITE | PROCESS_VM_READ, FALSE, pid);
    }
    if (!process) {
        return false;
    }

    size_t bytes = (injectPath.size() + 1) * sizeof(wchar_t);
    LPVOID remote = VirtualAllocEx(process, nullptr, bytes, MEM_COMMIT | MEM_RESERVE, PAGE_READWRITE);
    if (!remote) {
        CloseHandle(process);
        return false;
    }

    if (!WriteProcessMemory(process, remote, injectPath.c_str(), bytes, nullptr)) {
        VirtualFreeEx(process, remote, 0, MEM_RELEASE);
        CloseHandle(process);
        return false;
    }

    HMODULE kernel = GetModuleHandleW(L"kernel32.dll");
    auto loadLibraryW = reinterpret_cast<LPTHREAD_START_ROUTINE>(GetProcAddress(kernel, "LoadLibraryW"));
    if (!loadLibraryW) {
        VirtualFreeEx(process, remote, 0, MEM_RELEASE);
        CloseHandle(process);
        return false;
    }

    HANDLE thread = CreateRemoteThread(process, nullptr, 0, loadLibraryW, remote, 0, nullptr);
    if (!thread) {
        VirtualFreeEx(process, remote, 0, MEM_RELEASE);
        CloseHandle(process);
        return false;
    }

    WaitForSingleObject(thread, 30000);
    CloseHandle(thread);
    VirtualFreeEx(process, remote, 0, MEM_RELEASE);

    Sleep(120);
    HMODULE remoteMod = FindRemoteModule(process, injectPath);
    if (!remoteMod) {
        remoteMod = FindRemoteModule(process, dllPath);
    }
    if (!remoteMod) {
        remoteMod = FindRemoteModule(process, L"nvshim");
    }
    if (!remoteMod) {
        CloseHandle(process);
        return false;
    }

    HMODULE local = LoadLibraryW(injectPath.c_str());
    if (!local) {
        local = LoadLibraryW(dllPath.c_str());
    }
    if (!local) {
        CloseHandle(process);
        return false;
    }

    FARPROC localProc = GetProcAddress(local, "GhostInjectMain");
    if (!localProc) {
        FreeLibrary(local);
        CloseHandle(process);
        return false;
    }

    auto offset = reinterpret_cast<uintptr_t>(localProc) - reinterpret_cast<uintptr_t>(local);
    auto remoteProc = reinterpret_cast<LPTHREAD_START_ROUTINE>(
            reinterpret_cast<uintptr_t>(remoteMod) + offset);
    FreeLibrary(local);

    HANDLE run = CreateRemoteThread(process, nullptr, 0, remoteProc, nullptr, 0, nullptr);
    if (!run) {
        CloseHandle(process);
        return false;
    }
    WaitForSingleObject(run, 60000);
    DWORD code = 1;
    GetExitCodeThread(run, &code);
    CloseHandle(run);
    CloseHandle(process);
    return code == 0 || code == 6;
}

bool InjectDllWithRetries(DWORD pid, const std::wstring& dllPath, int attempts, DWORD delayMs) {
    AppendNativeInjectLog("InjectDllWithRetries pid=" + std::to_string(pid));
    if (attempts < 1) {
        attempts = 1;
    }
    for (int i = 0; i < attempts; i++) {
        if (InjectDll(pid, dllPath)) {
            AppendNativeInjectLog("InjectDllWithRetries success attempt=" + std::to_string(i + 1));
            return true;
        }
        if (i + 1 < attempts) {
            Sleep(delayMs);
        }
    }
    AppendNativeInjectLog("InjectDllWithRetries failed");
    return false;
}

std::wstring FindJavaHomeExe(DWORD minecraftPid) {
    if (minecraftPid != 0) {
        HANDLE process = OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION | PROCESS_VM_READ, FALSE, minecraftPid);
        if (process) {
            wchar_t path[MAX_PATH * 4] = {};
            DWORD size = MAX_PATH * 4;
            if (QueryFullProcessImageNameW(process, 0, path, &size) && path[0] != 0) {
                CloseHandle(process);
                fs::path p(path);
                // .../bin/javaw.exe -> prefer java.exe in same folder for attach
                fs::path dir = p.parent_path();
                fs::path java = dir / L"java.exe";
                if (fs::exists(java)) {
                    return java.wstring();
                }
                if (fs::exists(p)) {
                    return p.wstring();
                }
            } else {
                CloseHandle(process);
            }
        }
    }

    wchar_t javaHome[MAX_PATH * 2] = {};
    if (GetEnvironmentVariableW(L"JAVA_HOME", javaHome, MAX_PATH * 2) > 0) {
        fs::path cand = fs::path(javaHome) / L"bin" / L"java.exe";
        if (fs::exists(cand)) {
            return cand.wstring();
        }
    }

    const wchar_t* candidates[] = {
            L"C:\\Program Files\\BellSoft\\LibericaJDK-25\\bin\\java.exe",
            L"C:\\Program Files\\BellSoft\\LibericaJDK-21\\bin\\java.exe",
            L"C:\\Program Files\\Java\\jdk-21\\bin\\java.exe",
            L"C:\\Program Files\\Eclipse Adoptium\\jdk-21\\bin\\java.exe",
            L"C:\\Program Files\\Microsoft\\jdk-21\\bin\\java.exe",
    };
    for (const auto* c : candidates) {
        if (GetFileAttributesW(c) != INVALID_FILE_ATTRIBUTES) {
            return c;
        }
    }
    return L"java";
}

static bool JavaHasAttachModule(const fs::path& javaExe) {
    if (!fs::exists(javaExe)) {
        return false;
    }
    fs::path home = javaExe.parent_path().parent_path();
    // Full JDK markers: jmods/jdk.attach.jmod or jdk.attach package in lib
    if (fs::exists(home / L"jmods" / L"jdk.attach.jmod")) {
        return true;
    }
    // Liberica/older: tools.jar
    if (fs::exists(home / L"lib" / L"tools.jar")) {
        return true;
    }
    // jre-only runtimes (Minecraft) usually lack jmods entirely
    if (!fs::exists(home / L"jmods") && !fs::exists(home / L"lib" / L"modules")) {
        return false;
    }
    // modules image present — likely full enough with --add-modules
    if (fs::exists(home / L"lib" / L"modules")) {
        // Minecraft java-runtime-* often still lacks jdk.attach; detect by name
        std::wstring s = home.wstring();
        std::transform(s.begin(), s.end(), s.begin(), ::towlower);
        if (s.find(L"java-runtime") != std::wstring::npos
                || s.find(L"jre") != std::wstring::npos
                || s.find(L"minecraft") != std::wstring::npos) {
            return false;
        }
        return true;
    }
    return false;
}

// Prefer a full JDK for Attach API. Minecraft's bundled java-runtime is often a stripped JRE without jdk.attach.
std::wstring FindAttachJavaExe(DWORD minecraftPid) {
    auto tryJava = [](const fs::path& java) -> std::wstring {
        if (fs::exists(java) && JavaHasAttachModule(java)) {
            return java.wstring();
        }
        return L"";
    };

    wchar_t javaHome[MAX_PATH * 2] = {};
    if (GetEnvironmentVariableW(L"JAVA_HOME", javaHome, MAX_PATH * 2) > 0) {
        std::wstring hit = tryJava(fs::path(javaHome) / L"bin" / L"java.exe");
        if (!hit.empty()) {
            return hit;
        }
    }

    const wchar_t* candidates[] = {
            L"C:\\Program Files\\BellSoft\\LibericaJDK-25\\bin\\java.exe",
            L"C:\\Program Files\\BellSoft\\LibericaJDK-24\\bin\\java.exe",
            L"C:\\Program Files\\BellSoft\\LibericaJDK-21\\bin\\java.exe",
            L"C:\\Program Files\\BellSoft\\LibericaJDK-17\\bin\\java.exe",
            L"C:\\Program Files\\Java\\jdk-25\\bin\\java.exe",
            L"C:\\Program Files\\Java\\jdk-21\\bin\\java.exe",
            L"C:\\Program Files\\Java\\jdk-17\\bin\\java.exe",
            L"C:\\Program Files\\Eclipse Adoptium\\jdk-25\\bin\\java.exe",
            L"C:\\Program Files\\Eclipse Adoptium\\jdk-21\\bin\\java.exe",
            L"C:\\Program Files\\Microsoft\\jdk-21\\bin\\java.exe",
            L"C:\\Program Files\\Zulu\\zulu-25\\bin\\java.exe",
            L"C:\\Program Files\\Zulu\\zulu-21\\bin\\java.exe",
    };
    for (const auto* c : candidates) {
        std::wstring hit = tryJava(c);
        if (!hit.empty()) {
            return hit;
        }
    }

    // Search Program Files for any jdk*/bin/java.exe with attach
    const wchar_t* roots[] = {
            L"C:\\Program Files\\BellSoft",
            L"C:\\Program Files\\Java",
            L"C:\\Program Files\\Eclipse Adoptium",
            L"C:\\Program Files\\Microsoft",
            L"C:\\Program Files\\Zulu",
    };
    for (const auto* root : roots) {
        std::error_code ec;
        if (!fs::exists(root, ec)) {
            continue;
        }
        for (auto it = fs::directory_iterator(root, ec); !ec && it != fs::directory_iterator(); it.increment(ec)) {
            if (!it->is_directory()) {
                continue;
            }
            std::wstring hit = tryJava(it->path() / L"bin" / L"java.exe");
            if (!hit.empty()) {
                return hit;
            }
        }
    }

    // Last resort: MC process java (may fail without jdk.attach — DLL path still works)
    return FindJavaHomeExe(minecraftPid);
}

static std::wstring BuildAttachClasspath(const std::wstring& agentJar, const std::wstring& javaExe) {
    fs::path home = fs::path(javaExe).parent_path().parent_path();
    fs::path tools = home / L"lib" / L"tools.jar";
    std::wstring cp = agentJar;
    if (fs::exists(tools)) {
        cp += L";";
        cp += tools.wstring();
    }
    return cp;
}

bool AttachJavaAgent(DWORD pid, const RuntimePaths& paths, const std::string& attacherClass) {
    AppendNativeInjectLog("AttachJavaAgent pid=" + std::to_string(pid));
    if (pid == 0 || paths.agent.empty() || GetFileAttributesW(paths.agent.c_str()) == INVALID_FILE_ATTRIBUTES) {
        AppendNativeInjectLog("AttachJavaAgent aborted: missing pid or agent jar");
        return false;
    }

    // Copy agent/payload to short temp paths for attach reliability.
    std::wstring stageRoot = paths.root;
    EnsureDir(stageRoot);
    std::wstring agentCopy = stageRoot + L"\\nv-agent-" + std::to_wstring(GetTickCount64()) + L".jar";
    std::wstring payloadCopy = stageRoot + L"\\nv-payload-" + std::to_wstring(GetTickCount64()) + L".jar";
    std::wstring vanillaCopy = stageRoot + L"\\nv-payload-v-" + std::to_wstring(GetTickCount64()) + L".jar";
    CopyFileW(paths.agent.c_str(), agentCopy.c_str(), FALSE);
    CopyFileW(paths.payload.c_str(), payloadCopy.c_str(), FALSE);
    if (!paths.payloadVanilla.empty()) {
        CopyFileW(paths.payloadVanilla.c_str(), vanillaCopy.c_str(), FALSE);
    }
    if (GetFileAttributesW(agentCopy.c_str()) == INVALID_FILE_ATTRIBUTES) {
        agentCopy = paths.agent;
    }
    if (GetFileAttributesW(payloadCopy.c_str()) == INVALID_FILE_ATTRIBUTES) {
        payloadCopy = paths.payload;
    }
    if (GetFileAttributesW(vanillaCopy.c_str()) == INVALID_FILE_ATTRIBUTES) {
        vanillaCopy = paths.payloadVanilla.empty() ? payloadCopy : paths.payloadVanilla;
    }

    std::wstring javaExe = FindAttachJavaExe(pid);
    std::wstring attachClass = Widen(attacherClass);
    if (attachClass.empty()) {
        attachClass = L"nv.navineghost.injector.AgentAttacher";
    }

    std::wstring outLog = TempPath() + L"ghost-attach-out.log";
    DeleteFileW(outLog.c_str());

    // agentArgs: fabricPath|vanillaPath|force=...
    std::wstring forceArg;
    wchar_t forceEnv[32] = {};
    if (GetEnvironmentVariableW(L"GHOST_FORCE_LOADER", forceEnv, 32) > 0 && forceEnv[0] != L'\0') {
        forceArg = std::wstring(L"|force=") + forceEnv;
    } else {
#if defined(GHOST_BUILD_VANILLA)
        forceArg = L"|force=vanilla";
#elif defined(GHOST_BUILD_FABRIC)
        forceArg = L"|force=fabric";
#endif
    }
    std::wstring agentArgs = payloadCopy + L"|" + vanillaCopy + forceArg;
    std::wstring classpath = BuildAttachClasspath(agentCopy, javaExe);

    // jdk.attach is NOT on the default root set for bare -cp apps when java is a stripped runtime.
    // --add-modules jdk.attach works on full JDKs; tools.jar covers older JDKs.
    std::wstring args = L"\"" + javaExe + L"\""
            L" --add-modules jdk.attach"
            L" -Djdk.attach.allowAttachSelf=true"
            L" -cp \"" + classpath + L"\" "
            + attachClass + L" "
            + std::to_wstring(pid) + L" \"" + agentCopy + L"\" \"" + agentArgs + L"\"";

    SECURITY_ATTRIBUTES sa{sizeof(sa), nullptr, TRUE};
    HANDLE outFile = CreateFileW(outLog.c_str(), GENERIC_WRITE, FILE_SHARE_READ, &sa,
            CREATE_ALWAYS, FILE_ATTRIBUTE_NORMAL, nullptr);
    STARTUPINFOW si{sizeof(si)};
    si.dwFlags = STARTF_USESHOWWINDOW | STARTF_USESTDHANDLES;
    si.wShowWindow = SW_HIDE;
    si.hStdOutput = outFile ? outFile : GetStdHandle(STD_OUTPUT_HANDLE);
    si.hStdError = outFile ? outFile : GetStdHandle(STD_ERROR_HANDLE);
    si.hStdInput = GetStdHandle(STD_INPUT_HANDLE);
    PROCESS_INFORMATION pi{};
    std::vector<wchar_t> mutableCmd(args.begin(), args.end());
    mutableCmd.push_back(0);

    BOOL started = CreateProcessW(nullptr, mutableCmd.data(), nullptr, nullptr, TRUE,
            CREATE_NO_WINDOW, nullptr, nullptr, &si, &pi);
    if (outFile) {
        CloseHandle(outFile);
    }
    if (!started) {
        AppendNativeInjectLog("AttachJavaAgent CreateProcess failed err=" + std::to_string(GetLastError()));
        return false;
    }
    WaitForSingleObject(pi.hProcess, 60000);
    DWORD code = 1;
    GetExitCodeProcess(pi.hProcess, &code);
    CloseHandle(pi.hThread);
    CloseHandle(pi.hProcess);

    // Merge attach stdout into inject log for UI diagnostics.
    auto attachBody = ReadFileUtf8(fs::path(outLog));
    if (attachBody && !attachBody->empty()) {
        std::ofstream out(fs::path(TempPath()) / L"ghost-java-inject.log", std::ios::app);
        if (out) {
            out << "[ghost-attach-process] exit=" << code
                << " java=" << Narrow(javaExe) << "\n" << *attachBody << "\n";
        }
    }
    AppendNativeInjectLog("AttachJavaAgent exit=" + std::to_string(code));
    return code == 0;
}

static bool ProcessHasModule(HANDLE process, const wchar_t* needle) {
    HMODULE modules[1024];
    DWORD needed = 0;
    if (!EnumProcessModules(process, modules, sizeof(modules), &needed)) {
        return false;
    }
    const DWORD count = needed / sizeof(HMODULE);
    std::wstring want = needle;
    std::transform(want.begin(), want.end(), want.begin(), ::towlower);
    for (DWORD i = 0; i < count; i++) {
        wchar_t name[MAX_PATH] = {};
        if (GetModuleFileNameExW(process, modules[i], name, MAX_PATH) == 0) {
            continue;
        }
        std::wstring lower = name;
        std::transform(lower.begin(), lower.end(), lower.begin(), ::towlower);
        if (lower.find(want) != std::wstring::npos) {
            return true;
        }
    }
    return false;
}

static bool LooksLikeMinecraft(const std::wstring& commandLine) {
    std::wstring lower = commandLine;
    std::transform(lower.begin(), lower.end(), lower.begin(), ::towlower);
    if (lower.find(L"gradle") != std::wstring::npos
            || lower.find(L"idea64") != std::wstring::npos
            || lower.find(L"eclipse") != std::wstring::npos) {
        return false;
    }
    if (lower.find(L"ghostinjector") != std::wstring::npos
            || lower.find(L"navine-client-ghost") != std::wstring::npos
            || lower.find(L"navuryx-client-ghost") != std::wstring::npos
            || lower.find(L"navineghost") != std::wstring::npos
            || lower.find(L"navuryxghost") != std::wstring::npos
            || lower.find(L"ghost-inject") != std::wstring::npos
            || lower.find(L"vape421") != std::wstring::npos
            || lower.find(L"doomsdayinjector") != std::wstring::npos
            || lower.find(L"prestigeinjector") != std::wstring::npos
            || lower.find(L"electron") != std::wstring::npos
            || lower.find(L"runtime-helper") != std::wstring::npos) {
        return false;
    }
    return lower.find(L"net.fabricmc.loader") != std::wstring::npos
            || lower.find(L"knotclient") != std::wstring::npos
            || lower.find(L"net.minecraft.client.main") != std::wstring::npos
            || lower.find(L"net.minecraftforge") != std::wstring::npos
            || lower.find(L"net.neoforged") != std::wstring::npos
            || lower.find(L"cpw.mods") != std::wstring::npos
            || lower.find(L"curseforge\\minecraft\\instances") != std::wstring::npos
            || lower.find(L"curseforge/minecraft/instances") != std::wstring::npos
            || lower.find(L"prismlauncher") != std::wstring::npos
            || lower.find(L"--versiontype") != std::wstring::npos
            || lower.find(L"--gameDir") != std::wstring::npos
            || lower.find(L"--gamedir") != std::wstring::npos
            || lower.find(L".minecraft") != std::wstring::npos
            || lower.find(L"fabric-loader") != std::wstring::npos
            || lower.find(L"lunarclient") != std::wstring::npos
            || lower.find(L"com.moonsworth") != std::wstring::npos
            || lower.find(L"feather") != std::wstring::npos
            || lower.find(L"modrinth") != std::wstring::npos
            || lower.find(L"curseforge") != std::wstring::npos
            || lower.find(L"instances\\") != std::wstring::npos
            || lower.find(L"instances/") != std::wstring::npos
            || lower.find(L"--username") != std::wstring::npos
            || lower.find(L"--uuid") != std::wstring::npos
            || lower.find(L"--accessToken") != std::wstring::npos
            || lower.find(L"--accesstoken") != std::wstring::npos;
}

static int ScoreMinecraftCommand(const std::wstring& commandLine) {
    std::wstring lower = commandLine;
    std::transform(lower.begin(), lower.end(), lower.begin(), ::towlower);
    if (!LooksLikeMinecraft(lower)) {
        return -1;
    }
    int score = 50;
    if (lower.find(L"net.fabricmc.loader") != std::wstring::npos || lower.find(L"fabric-loader") != std::wstring::npos) {
        score += 40;
    }
    if (lower.find(L"net.minecraft.client.main") != std::wstring::npos) {
        score += 35;
    }
    if (lower.find(L"forge") != std::wstring::npos || lower.find(L"neoforge") != std::wstring::npos) {
        score += 30;
    }
    if (lower.find(L"lunar") != std::wstring::npos || lower.find(L"moonsworth") != std::wstring::npos) {
        score += 30;
    }
    if (lower.find(L"--gamedir") != std::wstring::npos || lower.find(L"--version") != std::wstring::npos) {
        score += 15;
    }
    if (lower.find(L"assets") != std::wstring::npos) {
        score += 10;
    }
    return score;
}

static std::string RunPowerShell(const std::wstring& command) {
    SECURITY_ATTRIBUTES sa{sizeof(sa), nullptr, TRUE};
    HANDLE readPipe = nullptr;
    HANDLE writePipe = nullptr;
    if (!CreatePipe(&readPipe, &writePipe, &sa, 0)) {
        return {};
    }
    SetHandleInformation(readPipe, HANDLE_FLAG_INHERIT, 0);

    std::wstring cmd = L"powershell -NoProfile -Command \"" + command + L"\"";
    STARTUPINFOW si{};
    si.cb = sizeof(si);
    si.dwFlags = STARTF_USESTDHANDLES;
    si.hStdOutput = writePipe;
    si.hStdError = writePipe;
    PROCESS_INFORMATION pi{};
    std::vector<wchar_t> mutableCmd(cmd.begin(), cmd.end());
    mutableCmd.push_back(0);
    if (!CreateProcessW(nullptr, mutableCmd.data(), nullptr, nullptr, TRUE, CREATE_NO_WINDOW, nullptr, nullptr, &si, &pi)) {
        CloseHandle(readPipe);
        CloseHandle(writePipe);
        return {};
    }
    CloseHandle(writePipe);
    std::string output;
    char buffer[4096];
    DWORD read = 0;
    while (ReadFile(readPipe, buffer, sizeof(buffer), &read, nullptr) && read > 0) {
        output.append(buffer, buffer + read);
    }
    WaitForSingleObject(pi.hProcess, 15000);
    CloseHandle(pi.hThread);
    CloseHandle(pi.hProcess);
    CloseHandle(readPipe);
    return output;
}

std::optional<DWORD> FindMinecraftJavaPid() {
    std::optional<DWORD> best;
    int bestScore = -1;

    HANDLE snap = CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS, 0);
    if (snap != INVALID_HANDLE_VALUE) {
        PROCESSENTRY32W pe{sizeof(pe)};
        if (Process32FirstW(snap, &pe)) {
            do {
                std::wstring name = pe.szExeFile;
                std::transform(name.begin(), name.end(), name.begin(), ::towlower);
                if (name != L"java.exe" && name != L"javaw.exe") {
                    continue;
                }
                DWORD pid = pe.th32ProcessID;
                if (pid == 0 || pid == GetCurrentProcessId()) {
                    continue;
                }
                HANDLE process = OpenProcess(PROCESS_QUERY_INFORMATION | PROCESS_VM_READ, FALSE, pid);
                if (!process) {
                    process = OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION | PROCESS_VM_READ, FALSE, pid);
                }
                if (!process) {
                    continue;
                }
                int score = 0;
                if (ProcessHasModule(process, L"jvm.dll")) {
                    score += 10;
                }
                if (ProcessHasModule(process, L"lwjgl") || ProcessHasModule(process, L"glfw")) {
                    score += 70;
                }
                if (ProcessHasModule(process, L"fabric-loader")
                        || ProcessHasModule(process, L"fabricmc")
                        || ProcessHasModule(process, L"intermediary")) {
                    score += 50;
                }
                if (ProcessHasModule(process, L"forge") || ProcessHasModule(process, L"neoforge")) {
                    score += 40;
                }
                CloseHandle(process);
                if (score > bestScore) {
                    bestScore = score;
                    best = pid;
                }
            } while (Process32NextW(snap, &pe));
        }
        CloseHandle(snap);
    }

    if (best && bestScore >= 10) {
        AppendNativeInjectLog("FindMinecraftJavaPid module-scan pid=" + std::to_string(*best)
                + " score=" + std::to_string(bestScore));
        return best;
    }

    std::string output = RunPowerShell(
            L"$OutputEncoding=[Console]::OutputEncoding=[Text.UTF8Encoding]::UTF8; "
            L"Get-CimInstance Win32_Process | "
            L"Where-Object { $_.Name -eq 'javaw.exe' -or $_.Name -eq 'java.exe' } | "
            L"ForEach-Object { $_.ProcessId.ToString() + '|' + ($_.CommandLine) }");
    std::istringstream stream(output);
    std::string line;
    while (std::getline(stream, line)) {
        auto pos = line.find('|');
        if (pos == std::string::npos) {
            continue;
        }
        std::string pidStr = line.substr(0, pos);
        std::string cmdLine = line.substr(pos + 1);
        int needed = MultiByteToWideChar(CP_UTF8, 0, cmdLine.c_str(), -1, nullptr, 0);
        std::wstring wcmd;
        if (needed > 1) {
            wcmd.resize(static_cast<size_t>(needed - 1));
            MultiByteToWideChar(CP_UTF8, 0, cmdLine.c_str(), -1, wcmd.data(), needed);
        } else {
            wcmd.assign(cmdLine.begin(), cmdLine.end());
        }
        int score = ScoreMinecraftCommand(wcmd);
        if (score < 0) {
            continue;
        }
        DWORD pid = static_cast<DWORD>(strtoul(pidStr.c_str(), nullptr, 10));
        if (pid == 0 || pid == GetCurrentProcessId()) {
            continue;
        }
        if (score > bestScore) {
            bestScore = score;
            best = pid;
        }
    }
    if (best && bestScore >= 10) {
        AppendNativeInjectLog("FindMinecraftJavaPid cmdline pid=" + std::to_string(*best)
                + " score=" + std::to_string(bestScore));
        return best;
    }

    snap = CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS, 0);
    if (snap != INVALID_HANDLE_VALUE) {
        PROCESSENTRY32W pe{sizeof(pe)};
        if (Process32FirstW(snap, &pe)) {
            do {
                std::wstring name = pe.szExeFile;
                std::transform(name.begin(), name.end(), name.begin(), ::towlower);
                if (name != L"java.exe" && name != L"javaw.exe") {
                    continue;
                }
                DWORD pid = pe.th32ProcessID;
                if (pid == 0 || pid == GetCurrentProcessId()) {
                    continue;
                }
                HANDLE process = OpenProcess(PROCESS_QUERY_INFORMATION | PROCESS_VM_READ, FALSE, pid);
                if (!process) {
                    process = OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION | PROCESS_VM_READ, FALSE, pid);
                }
                if (!process) {
                    continue;
                }
                bool hasJvm = ProcessHasModule(process, L"jvm.dll");
                CloseHandle(process);
                if (hasJvm) {
                    AppendNativeInjectLog("FindMinecraftJavaPid fallback jvm pid=" + std::to_string(pid));
                    CloseHandle(snap);
                    return pid;
                }
            } while (Process32NextW(snap, &pe));
        }
        CloseHandle(snap);
    }
    AppendNativeInjectLog("FindMinecraftJavaPid not found");
    return std::nullopt;
}

std::optional<DWORD> WaitForMinecraftJavaPid(DWORD timeoutMs) {
    DWORD start = GetTickCount();
    while (GetTickCount() - start < timeoutMs) {
        auto pid = FindMinecraftJavaPid();
        if (pid) {
            return pid;
        }
        Sleep(200);
    }
    return FindMinecraftJavaPid();
}

void KillMinecraftJava() {
    auto pid = FindMinecraftJavaPid();
    while (pid) {
        HANDLE process = OpenProcess(PROCESS_TERMINATE, FALSE, *pid);
        if (process) {
            TerminateProcess(process, 1);
            CloseHandle(process);
        }
        Sleep(300);
        pid = FindMinecraftJavaPid();
    }
}

std::wstring GetExeDir() {
    wchar_t path[MAX_PATH] = {};
    GetModuleFileNameW(nullptr, path, MAX_PATH);
    fs::path p(path);
    return p.parent_path().wstring();
}

bool ShellOpen(const std::wstring& pathOrUri) {
    HINSTANCE result = ShellExecuteW(nullptr, L"open", pathOrUri.c_str(), nullptr, nullptr, SW_SHOWNORMAL);
    return reinterpret_cast<INT_PTR>(result) > 32;
}

static std::optional<std::string> ReadFileUtf8(const fs::path& path) {
    std::ifstream in(path, std::ios::binary);
    if (!in) {
        return std::nullopt;
    }
    std::ostringstream ss;
    ss << in.rdbuf();
    return ss.str();
}

static bool WriteFileUtf8(const fs::path& path, const std::string& content) {
    std::ofstream out(path, std::ios::binary | std::ios::trunc);
    if (!out) {
        return false;
    }
    out.write(content.data(), static_cast<std::streamsize>(content.size()));
    return static_cast<bool>(out);
}

static std::optional<std::string> ExtractJsonString(const std::string& json, const std::string& key) {
    std::string needle = "\"" + key + "\":\"";
    auto pos = json.find(needle);
    if (pos == std::string::npos) {
        return std::nullopt;
    }
    pos += needle.size();
    auto end = json.find('"', pos);
    if (end == std::string::npos) {
        return std::nullopt;
    }
    return json.substr(pos, end - pos);
}

static bool ReplaceJavaArgsOverride(std::string& json, const std::string& argsValue) {
    std::string replacement = "\"javaArgsOverride\":\"" + EscapeJson(argsValue) + "\"";
    std::regex pattern("\"javaArgsOverride\":(?:null|\"(?:\\\\.|[^\"])*\")");
    if (std::regex_search(json, pattern)) {
        json = std::regex_replace(json, pattern, replacement, std::regex_constants::format_first_only);
        return true;
    }
    auto unlocked = json.find("\"isUnlocked\"");
    if (unlocked != std::string::npos) {
        json.insert(unlocked, replacement + ",");
        return true;
    }
    return false;
}

static std::wstring ToLowerW(std::wstring value) {
    std::transform(value.begin(), value.end(), value.begin(), ::towlower);
    return value;
}

static std::string ToLowerA(std::string value) {
    std::transform(value.begin(), value.end(), value.begin(),
            [](unsigned char c) { return static_cast<char>(std::tolower(c)); });
    return value;
}

static bool IsInstanceDir(const fs::path& directory) {
    return fs::is_directory(directory / L"mods")
            || fs::exists(directory / L"minecraftinstance.json")
            || fs::exists(directory / L"instance.cfg")
            || fs::exists(directory / L"mmc-pack.json")
            || fs::exists(directory / L"options.txt");
}

static void CollectRootInstances(const fs::path& root, std::vector<fs::path>& out) {
    if (root.empty() || !fs::exists(root)) {
        return;
    }
    std::error_code ec;
    if (IsInstanceDir(root)) {
        out.push_back(root);
        return;
    }
    for (auto& entry : fs::directory_iterator(root, ec)) {
        if (!entry.is_directory()) {
            continue;
        }
        if (IsInstanceDir(entry.path())) {
            out.push_back(entry.path());
        }
    }
}

static std::vector<fs::path> DiscoverInstanceRoots() {
    wchar_t userProfile[MAX_PATH] = {};
    GetEnvironmentVariableW(L"USERPROFILE", userProfile, MAX_PATH);
    std::wstring appData = AppData();
    std::wstring local = LocalAppData();
    std::vector<fs::path> roots = {
            fs::path(appData) / L".minecraft",
            fs::path(userProfile) / L"curseforge" / L"minecraft" / L"Instances",
            fs::path(userProfile) / L"curseforge" / L"minecraft" / L"Installations",
            fs::path(userProfile) / L"Documents" / L"curseforge" / L"minecraft" / L"Instances",
            fs::path(userProfile) / L"Documents" / L"CurseForge" / L"Minecraft" / L"Instances",
            fs::path(appData) / L"CurseForge" / L"Minecraft" / L"Instances",
            fs::path(local) / L"CurseForge" / L"Minecraft" / L"Instances",
            fs::path(appData) / L"PrismLauncher" / L"instances",
            fs::path(appData) / L"PrismLauncher-cracked" / L"instances",
            fs::path(appData) / L"PolyMC" / L"instances",
            fs::path(appData) / L"MultiMC" / L"instances",
            fs::path(appData) / L"ATLauncher" / L"instances",
            fs::path(appData) / L"gdlauncher_next" / L"instances",
            fs::path(appData) / L"gdlauncher" / L"instances",
            fs::path(appData) / L"ModrinthApp" / L"profiles",
            fs::path(local) / L"ModrinthApp" / L"profiles",
            fs::path(userProfile) / L".lunarclient" / L"offline" / L"multiver",
            fs::path(userProfile) / L".lunarclient" / L"offline",
            fs::path(userProfile) / L".feather" / L"player-data",
            fs::path(appData) / L"feather-launcher" / L"instances",
            fs::path(local) / L"Feather Launcher" / L"instances",
            fs::path(appData) / L"Launcher" / L"instances",
            fs::path(appData) / L"SKLauncher" / L"instances",
            fs::path(appData) / L"TLauncher" / L"legacy" / L"Minecraft" / L"game",
            fs::path(appData) / L".tlauncher" / L"legacy" / L"Minecraft" / L"game",
            fs::path(userProfile) / L"scoop" / L"apps" / L"prismlauncher" / L"current" / L"instances",
            fs::path(userProfile) / L"AppData" / L"Roaming" / L"PrismLauncher" / L"instances",
    };
    return roots;
}

static bool PathExistsAny(const std::vector<fs::path>& paths) {
    for (const auto& p : paths) {
        if (fs::exists(p)) {
            return true;
        }
    }
    return false;
}

static bool FileNameContainsIgnoreCase(const fs::path& dir, const wchar_t* needle) {
    if (!fs::exists(dir) || !fs::is_directory(dir)) {
        return false;
    }
    std::error_code ec;
    std::wstring want = needle;
    std::transform(want.begin(), want.end(), want.begin(), ::towlower);
    for (auto& entry : fs::directory_iterator(dir, ec)) {
        auto name = entry.path().filename().wstring();
        std::transform(name.begin(), name.end(), name.begin(), ::towlower);
        if (name.find(want) != std::wstring::npos) {
            return true;
        }
    }
    return false;
}

static std::string ReadInstanceBlob(const fs::path& instance);

bool InstanceLooksLikeFabric(const std::wstring& instancePath) {
    if (instancePath.empty()) {
        return false;
    }
    fs::path root(instancePath);
    auto blob = ReadInstanceBlob(root);
    std::string lower = ToLowerA(blob);
    if (lower.find("net.fabricmc") != std::string::npos
            || lower.find("\"fabric\"") != std::string::npos
            || lower.find("fabric-loader") != std::string::npos
            || lower.find("fabric loader") != std::string::npos) {
        return true;
    }
    if (FileNameContainsIgnoreCase(root / L"mods", L"fabric-api")
            || FileNameContainsIgnoreCase(root / L"mods", L"fabric-loader")
            || FileNameContainsIgnoreCase(root / L"mods", L"fabric")) {
        return true;
    }
    if (FileNameContainsIgnoreCase(root / L"versions", L"fabric")
            || FileNameContainsIgnoreCase(root / L"libraries" / L"net" / L"fabricmc", L"fabric")) {
        return true;
    }
    fs::path versions = root / L"versions";
    if (fs::exists(versions)) {
        std::error_code ec;
        for (auto& entry : fs::directory_iterator(versions, ec)) {
            if (!entry.is_directory()) {
                continue;
            }
            auto name = ToLowerW(entry.path().filename().wstring());
            if (name.find(L"fabric") != std::wstring::npos) {
                return true;
            }
        }
    }
    return false;
}

bool InstanceLooksLikeForge(const std::wstring& instancePath) {
    if (instancePath.empty()) {
        return false;
    }
    fs::path root(instancePath);
    auto blob = ReadInstanceBlob(root);
    std::string lower = ToLowerA(blob);
    if ((lower.find("net.minecraftforge") != std::string::npos
                || lower.find("net.neoforged") != std::string::npos
                || lower.find("\"forge\"") != std::string::npos
                || lower.find("neoforge") != std::string::npos)
            && lower.find("fabric") == std::string::npos) {
        return true;
    }
    if (FileNameContainsIgnoreCase(root / L"mods", L"forge")
            || FileNameContainsIgnoreCase(root / L"versions", L"forge")
            || FileNameContainsIgnoreCase(root / L"versions", L"neoforge")) {
        return true;
    }
    std::wstring pathLower = ToLowerW(instancePath);
    if ((pathLower.find(L"forge") != std::wstring::npos || pathLower.find(L"neoforge") != std::wstring::npos)
            && pathLower.find(L"fabric") == std::wstring::npos) {
        return true;
    }
    return false;
}

std::wstring DetectLoaderLabel(const std::wstring& instancePath) {
    if (instancePath.empty()) {
        return L"";
    }
    if (InstanceLooksLikeFabric(instancePath)) {
        return L"Fabric";
    }
    if (InstanceLooksLikeForge(instancePath)) {
        return L"Forge";
    }
    return L"Vanilla";
}

std::wstring DetectLauncherName(const std::wstring& instancePath) {
    std::wstring lower = ToLowerW(instancePath);
    if (lower.find(L"curseforge") != std::wstring::npos) {
        return L"CurseForge";
    }
    if (lower.find(L"prismlauncher") != std::wstring::npos || lower.find(L"prism") != std::wstring::npos) {
        return L"Prism";
    }
    if (lower.find(L"modrinth") != std::wstring::npos) {
        return L"Modrinth";
    }
    if (lower.find(L"multimc") != std::wstring::npos || lower.find(L"polymc") != std::wstring::npos) {
        return L"MultiMC";
    }
    if (lower.find(L"atlauncher") != std::wstring::npos) {
        return L"ATLauncher";
    }
    if (lower.find(L"gdlauncher") != std::wstring::npos) {
        return L"GDLauncher";
    }
    if (lower.find(L"lunarclient") != std::wstring::npos || lower.find(L".lunarclient") != std::wstring::npos) {
        return L"Lunar";
    }
    if (lower.find(L"feather") != std::wstring::npos) {
        return L"Feather";
    }
    if (lower.find(L"tlauncher") != std::wstring::npos) {
        return L"TLauncher";
    }
    if (lower.find(L"sklauncher") != std::wstring::npos) {
        return L"SKLauncher";
    }
    if (lower.find(L".minecraft") != std::wstring::npos) {
        return L"Official";
    }
    return L"Unknown";
}

static bool TryLaunchExe(const fs::path& exe, const std::wstring& args = L"") {
    if (!fs::exists(exe)) {
        return false;
    }
    std::wstring cmd = L"\"" + exe.wstring() + L"\"";
    if (!args.empty()) {
        cmd += L" " + args;
    }
    STARTUPINFOW si{sizeof(si)};
    PROCESS_INFORMATION pi{};
    std::vector<wchar_t> mutableCmd(cmd.begin(), cmd.end());
    mutableCmd.push_back(0);
    if (CreateProcessW(nullptr, mutableCmd.data(), nullptr, nullptr, FALSE, 0, nullptr, nullptr, &si, &pi)) {
        CloseHandle(pi.hThread);
        CloseHandle(pi.hProcess);
        return true;
    }
    return ShellOpen(exe.wstring());
}

std::optional<std::wstring> FindPreferredInstanceGuid(const std::wstring& preferredVersion);
bool LaunchCurseForgeInstance(const std::wstring& guid);

bool LaunchMinecraftAny(const std::wstring& instancePath) {
    std::optional<std::wstring> guid;
    if (!instancePath.empty() && fs::exists(fs::path(instancePath) / L"minecraftinstance.json")) {
        auto json = ReadFileUtf8(fs::path(instancePath) / L"minecraftinstance.json");
        if (json) {
            auto g = ExtractJsonString(*json, "guid");
            if (g) {
                guid = Widen(*g);
            }
        }
    }
    if (!guid) {
        guid = FindPreferredInstanceGuid(L"");
    }
    if (guid && LaunchCurseForgeInstance(*guid)) {
        return true;
    }

    std::wstring local = LocalAppData();
    std::wstring appData = AppData();
    wchar_t programFiles[MAX_PATH] = {};
    wchar_t programFilesX86[MAX_PATH] = {};
    GetEnvironmentVariableW(L"ProgramFiles", programFiles, MAX_PATH);
    GetEnvironmentVariableW(L"ProgramFiles(x86)", programFilesX86, MAX_PATH);
    wchar_t userProfile[MAX_PATH] = {};
    GetEnvironmentVariableW(L"USERPROFILE", userProfile, MAX_PATH);

    std::vector<fs::path> launchers = {
            fs::path(local) / L"Programs" / L"minecraft-launcher" / L"MinecraftLauncher.exe",
            fs::path(local) / L"Programs" / L"Minecraft Launcher" / L"MinecraftLauncher.exe",
            fs::path(programFilesX86) / L"Minecraft Launcher" / L"MinecraftLauncher.exe",
            fs::path(programFiles) / L"Minecraft Launcher" / L"MinecraftLauncher.exe",
            fs::path(local) / L"Programs" / L"Lunar Client" / L"Lunar Client.exe",
            fs::path(local) / L"Programs" / L"lunarclient" / L"Lunar Client.exe",
            fs::path(local) / L"Programs" / L"Feather Launcher" / L"Feather Launcher.exe",
            fs::path(local) / L"Programs" / L"feather-launcher" / L"Feather Launcher.exe",
            fs::path(appData) / L"PrismLauncher" / L"prismlauncher.exe",
            fs::path(local) / L"Programs" / L"PrismLauncher" / L"prismlauncher.exe",
            fs::path(local) / L"Programs" / L"Modrinth App" / L"Modrinth App.exe",
            fs::path(local) / L"Programs" / L"curseforge" / L"CurseForge.exe",
            fs::path(local) / L"Programs" / L"CurseForge Windows" / L"CurseForge.exe",
            fs::path(userProfile) / L"AppData" / L"Local" / L"Programs" / L"feather" / L"Feather Launcher.exe",
    };

    std::wstring launcher = DetectLauncherName(instancePath);
    if (launcher == L"Prism" || launcher == L"MultiMC") {
        for (const auto& exe : launchers) {
            auto name = ToLowerW(exe.filename().wstring());
            if (name.find(L"prism") != std::wstring::npos || name.find(L"multimc") != std::wstring::npos) {
                if (TryLaunchExe(exe)) {
                    return true;
                }
            }
        }
    }
    if (launcher == L"Lunar") {
        for (const auto& exe : launchers) {
            if (ToLowerW(exe.wstring()).find(L"lunar") != std::wstring::npos && TryLaunchExe(exe)) {
                return true;
            }
        }
    }
    if (launcher == L"Feather") {
        for (const auto& exe : launchers) {
            if (ToLowerW(exe.wstring()).find(L"feather") != std::wstring::npos && TryLaunchExe(exe)) {
                return true;
            }
        }
    }
    if (launcher == L"Official") {
        for (const auto& exe : launchers) {
            if (ToLowerW(exe.filename().wstring()).find(L"minecraftlauncher") != std::wstring::npos && TryLaunchExe(exe)) {
                return true;
            }
        }
    }
    if (launcher == L"Modrinth") {
        for (const auto& exe : launchers) {
            if (ToLowerW(exe.wstring()).find(L"modrinth") != std::wstring::npos && TryLaunchExe(exe)) {
                return true;
            }
        }
    }

    for (const auto& exe : launchers) {
        if (TryLaunchExe(exe)) {
            return true;
        }
    }

    if (!instancePath.empty()) {
        OpenFolder(instancePath);
    }
    return false;
}

static int ScoreGameVersionText(const std::string& textLower) {
    int best = 0;
    std::regex pattern26(R"(26\.(\d+)(?:\.(\d+))?(-snapshot(?:-\d+)?)?)");
    auto begin = std::sregex_iterator(textLower.begin(), textLower.end(), pattern26);
    auto end = std::sregex_iterator();
    for (auto it = begin; it != end; ++it) {
        int minor = std::stoi((*it)[1].str());
        int patch = (*it)[2].matched ? std::stoi((*it)[2].str()) : 0;
        bool snapshot = (*it)[3].matched;
        int score = 0;
        if (minor == 1) {
            score = 80 + (patch >= 2 ? 12 : (patch == 1 ? 8 : 4));
        } else if (minor == 2) {
            score = 94 + (patch > 0 ? 2 : 0);
        } else if (minor == 3) {
            score = 92 - (snapshot ? 4 : 0);
        } else if (minor >= 4 && minor <= 9) {
            score = 70;
        } else {
            score = 40;
        }
        if (score > best) {
            best = score;
        }
    }
    std::regex pattern21(R"(1\.21(?:\.(\d+))?)");
    begin = std::sregex_iterator(textLower.begin(), textLower.end(), pattern21);
    for (auto it = begin; it != end; ++it) {
        int patch = (*it)[1].matched ? std::stoi((*it)[1].str()) : 0;
        int score = 60 + (patch < 11 ? patch : 11);
        if (score > best) {
            best = score;
        }
    }
    if (best == 0 && (textLower.find("26.") != std::string::npos || textLower.find("1.21") != std::string::npos)) {
        best = 35;
    }
    return best;
}

static std::optional<std::string> ExtractVersionFromText(const std::string& text) {
    std::regex pattern26(R"(26\.\d+(?:\.\d+)?(?:-snapshot(?:-\d+)?)?)");
    std::smatch match;
    if (std::regex_search(text, match, pattern26)) {
        return match.str(0);
    }
    std::regex pattern21(R"(1\.21(?:\.\d+)?)");
    if (std::regex_search(text, match, pattern21)) {
        return match.str(0);
    }
    return std::nullopt;
}

static std::string ReadInstanceBlob(const fs::path& instance) {
    std::string blob;
    auto appendFile = [&](const fs::path& file) {
        auto content = ReadFileUtf8(file);
        if (content) {
            blob += *content;
            blob.push_back('\n');
        }
    };
    appendFile(instance / L"minecraftinstance.json");
    appendFile(instance / L"mmc-pack.json");
    appendFile(instance / L"instance.cfg");
    appendFile(instance / L"config.json");
    appendFile(instance / L"profile.json");
    return blob;
}

std::optional<std::wstring> DetectGameVersion(const std::wstring& instancePath) {
    if (instancePath.empty()) {
        return std::nullopt;
    }
    fs::path instance(instancePath);
    auto json = ReadFileUtf8(instance / L"minecraftinstance.json");
    if (json) {
        for (const char* key : {"gameVersion", "minecraftVersion", "baseMinecraftVersion", "version"}) {
            auto value = ExtractJsonString(*json, key);
            if (value && !value->empty()) {
                return Widen(*value);
            }
        }
        auto extracted = ExtractVersionFromText(ToLowerA(*json));
        if (extracted) {
            return Widen(*extracted);
        }
    }
    auto blob = ReadInstanceBlob(instance);
    auto extracted = ExtractVersionFromText(ToLowerA(blob));
    if (extracted) {
        return Widen(*extracted);
    }
    return std::nullopt;
}

bool IsSupportedGameVersion(const std::wstring& version) {
    if (version.empty()) {
        return false;
    }
    std::string lower = ToLowerA(Narrow(version));
    if (lower.find("1.21") != std::string::npos) {
        return true;
    }
    int score = ScoreGameVersionText(lower);
    return score >= 70;
}

std::wstring SupportedVersionsLabel() {
    return L"1.8.9 – 26.5";
}

std::vector<std::wstring> SupportedPickerVersions() {
    return {
            L"1.8.9",
            L"1.12.2",
            L"1.21.11",
            L"1.21.4",
            L"26",
            L"26.1",
            L"26.1.2",
            L"26.2",
            L"26.3",
            L"26.4",
            L"26.5"
    };
}

static int ScoreInstancePath(const fs::path& instance, const std::wstring& preferredVersion) {
    int score = 0;
    std::wstring pathLower = ToLowerW(instance.wstring());
    if (pathLower.find(L"navine") != std::wstring::npos) {
        score += 120;
    }
    if (pathLower.find(L"navuryx") != std::wstring::npos) {
        score += 120;
    }
    if (pathLower.find(L"ghost") != std::wstring::npos) {
        score += 40;
    }
    if (pathLower.find(L"prestige") != std::wstring::npos) {
        score += 80;
    }
    if (pathLower.find(L"doomsday") != std::wstring::npos) {
        score += 120;
    }
    if (pathLower.find(L"navine ghost client") != std::wstring::npos
            || pathLower.find(L"navuryx ghost client") != std::wstring::npos) {
        score += 200;
    }
    if (pathLower.find(L"fabric") != std::wstring::npos) {
        score += 20;
    }
    if (pathLower.find(L"lunar") != std::wstring::npos || pathLower.find(L"feather") != std::wstring::npos) {
        score -= 30;
    }

    auto blob = ReadInstanceBlob(instance);
    std::string blobLower = ToLowerA(blob);
    if (!blobLower.empty()) {
        score += 10;
        int versionScore = ScoreGameVersionText(blobLower);
        score += versionScore;
        if (blobLower.find("fabric") != std::string::npos
                || blobLower.find("net.fabricmc") != std::string::npos
                || blobLower.find("\"name\":\"fabric\"") != std::string::npos) {
            score += 80;
        }
        if (blobLower.find("forge") != std::string::npos && blobLower.find("fabric") == std::string::npos) {
            score -= 80;
        }
        if (blobLower.find("neoforge") != std::string::npos && blobLower.find("fabric") == std::string::npos) {
            score -= 80;
        }
    }

    if (InstanceLooksLikeFabric(instance.wstring())) {
        score += 50;
    }
    if (pathLower.find(L"forge") != std::wstring::npos) {
        score += 25;
    }
    if (pathLower.find(L"vanilla") != std::wstring::npos) {
        score += 10;
    }
    if (pathLower.find(L"lunar") != std::wstring::npos || pathLower.find(L"feather") != std::wstring::npos) {
        score += 15;
    }

    if (!preferredVersion.empty()) {
        std::string pref = ToLowerA(Narrow(preferredVersion));
        auto detected = DetectGameVersion(instance.wstring());
        std::string det = detected ? ToLowerA(Narrow(*detected)) : "";
        if (!det.empty() && (det == pref || det.find(pref) != std::string::npos || pref.find(det) != std::string::npos)) {
            score += 220;
        } else if (!blobLower.empty() && blobLower.find(pref) != std::string::npos) {
            score += 180;
        } else {
            score -= 40;
        }
    }

    if (fs::exists(instance / L"minecraftinstance.json")) {
        score += 15;
    }
    if (fs::is_directory(instance / L"mods")) {
        score += 10;
    }
    if (fs::exists(instance / L"mmc-pack.json") || fs::exists(instance / L"instance.cfg")) {
        score += 8;
    }
    return score;
}

std::optional<std::wstring> FindPreferredInstancePath(const std::wstring& preferredVersion) {
    std::vector<fs::path> candidates;
    for (const auto& root : DiscoverInstanceRoots()) {
        CollectRootInstances(root, candidates);
    }
    if (candidates.empty()) {
        return std::nullopt;
    }

    std::optional<fs::path> best;
    int bestScore = -1;
    for (const auto& candidate : candidates) {
        int score = ScoreInstancePath(candidate, preferredVersion);
        if (score > bestScore) {
            bestScore = score;
            best = candidate;
        }
    }
    if (!best || bestScore <= 0) {
        return candidates.front().wstring();
    }
    return best->wstring();
}

std::optional<std::wstring> FindPreferredInstanceGuid(const std::wstring& preferredVersion) {
    auto path = FindPreferredInstancePath(preferredVersion);
    if (!path) {
        return std::nullopt;
    }
    auto json = ReadFileUtf8(fs::path(*path) / L"minecraftinstance.json");
    if (!json) {
        return std::nullopt;
    }
    auto guid = ExtractJsonString(*json, "guid");
    if (!guid) {
        return std::nullopt;
    }
    return Widen(*guid);
}

bool LaunchCurseForgeInstance(const std::wstring& guid) {
    std::wstring uri = L"curseforge://launch-game?instanceId=" + guid + L"&gameId=432";
    wchar_t local[MAX_PATH] = {};
    GetEnvironmentVariableW(L"LOCALAPPDATA", local, MAX_PATH);
    fs::path cf = fs::path(local) / L"Programs" / L"CurseForge Windows" / L"CurseForge.exe";
    if (fs::exists(cf)) {
        std::wstring cmd = L"\"" + cf.wstring() + L"\" " + uri;
        STARTUPINFOW si{sizeof(si)};
        PROCESS_INFORMATION pi{};
        std::vector<wchar_t> mutableCmd(cmd.begin(), cmd.end());
        mutableCmd.push_back(0);
        if (CreateProcessW(nullptr, mutableCmd.data(), nullptr, nullptr, FALSE, 0, nullptr, nullptr, &si, &pi)) {
            CloseHandle(pi.hThread);
            CloseHandle(pi.hProcess);
            return true;
        }
    }
    return ShellOpen(uri);
}

bool DeployStealthPayload(const RuntimePaths& paths, std::wstring& stealthPayloadOut) {
    fs::path stealthDir = fs::path(AppData()) / L".minecraft" / L"libraries" / L"com" / L"mojang"
            / L"datafixerupper" / L"8.0.16";
    EnsureDir(stealthDir.wstring());
    fs::path stealth = stealthDir / L"datafixerupper-8.0.16-runtime.jar";
    if (!CopyFileW(paths.payload.c_str(), stealth.c_str(), FALSE)) {
        return false;
    }
    stealthPayloadOut = stealth.wstring();
    return true;
}

bool InstallLaunchArgs(const RuntimePaths& paths, const std::wstring& instancePath) {
    (void)paths;
    (void)instancePath;
    // Inject-only: never patch JVM args or javaagent for CurseForge / instances.
    return false;
}

bool DeployModsFolderFallback(const RuntimePaths& paths, const std::wstring& instancePath) {
    (void)paths;
    (void)instancePath;
    // Inject-only product: never write jars into the user's mods folder.
    return false;
}

static void DeleteIfGhostJar(const fs::path& file, int& removed) {
    if (!fs::is_regular_file(file)) {
        return;
    }
    auto name = ToLowerW(file.filename().wstring());
    if (name == L"ghost-client.jar"
            || name == L"navine-ghost-client.jar"
            || name == L"navuryx-ghost-client.jar"
            || name == L"zz-navine-ghost.jar"
            || name == L"1ghost-client.jar"
            || name.find(L"navine-ghost") != std::wstring::npos
            || name.find(L"navuryx-ghost") != std::wstring::npos) {
        if (DeleteFileW(file.wstring().c_str())) {
            removed++;
        }
    }
}

static void SweepModsDir(const fs::path& modsDir, int& removed) {
    if (!fs::exists(modsDir) || !fs::is_directory(modsDir)) {
        return;
    }
    std::error_code ec;
    for (auto& entry : fs::directory_iterator(modsDir, ec)) {
        if (entry.is_regular_file()) {
            DeleteIfGhostJar(entry.path(), removed);
        }
    }
}

int RemoveGhostInstallArtifacts(const std::wstring& instancePath) {
    if (instancePath.empty()) {
        return 0;
    }
    int removed = 0;
    fs::path root(instancePath);
    SweepModsDir(root / L"mods", removed);
    SweepModsDir(root / L".minecraft" / L"mods", removed);
    SweepModsDir(root / L"minecraft" / L"mods", removed);

    // Clear prior javaagent / fabric.addMods overrides this injector may have written.
    CleanupLaunchArgs(instancePath);

    fs::path stealth = fs::path(AppData()) / L".minecraft" / L"libraries" / L"com" / L"mojang"
            / L"datafixerupper" / L"8.0.16" / L"datafixerupper-8.0.16-runtime.jar";
    if (fs::exists(stealth)) {
        if (DeleteFileW(stealth.wstring().c_str())) {
            removed++;
        }
    }
    return removed;
}

bool WaitForGhostInLog(const std::wstring& instancePath, DWORD timeoutMs) {
    fs::path logPath = fs::path(instancePath) / L"logs" / L"latest.log";
    fs::path altLog = fs::path(instancePath) / L".minecraft" / L"logs" / L"latest.log";
    DWORD start = GetTickCount();
    while (GetTickCount() - start < timeoutMs) {
        auto check = [](const fs::path& path) -> bool {
            auto content = ReadFileUtf8(path);
            if (!content) {
                return false;
            }
            return content->find("navine-ghost") != std::string::npos
                    || content->find("navuryx-ghost") != std::string::npos
                    || content->find("Navine-Ghost-Client") != std::string::npos
                    || content->find("Navuryx-Ghost-Client") != std::string::npos
                    || content->find("[ghost-inject]") != std::string::npos
                    || content->find("ghost payload active") != std::string::npos;
        };
        if (check(logPath) || check(altLog)) {
            return true;
        }
        Sleep(400);
    }
    return false;
}

static bool DllInjectLogShowsSuccess() {
    auto content = ReadFileUtf8(fs::path(TempPath()) / L"ghost-java-inject.log");
    if (!content) {
        return false;
    }
    return content->find("GhostAgent.inject invoked") != std::string::npos
            || content->find("loader props set") != std::string::npos
            || content->find("inject worker done") != std::string::npos
            || content->find("inject worker start") != std::string::npos
            || content->find("client bootstrapped") != std::string::npos
            || content->find("clientStarted=true") != std::string::npos
            || content->find("attached payload to system classloader") != std::string::npos
            || content->find("[ghost-attach]") != std::string::npos
            || content->find("[ghost-inject]") != std::string::npos;
}

bool WaitForInjectEvidence(const std::optional<std::wstring>& instancePath, DWORD timeoutMs) {
    DWORD start = GetTickCount();
    while (GetTickCount() - start < timeoutMs) {
        if (DllInjectLogShowsSuccess()) {
            return true;
        }
        if (instancePath) {
            fs::path logPath = fs::path(*instancePath) / L"logs" / L"latest.log";
            fs::path altLog = fs::path(*instancePath) / L".minecraft" / L"logs" / L"latest.log";
            auto content = ReadFileUtf8(logPath);
            if (!content) {
                content = ReadFileUtf8(altLog);
            }
            if (content && (content->find("[ghost-inject]") != std::string::npos
                    || content->find("ghost payload active") != std::string::npos
                    || content->find("navine-ghost") != std::string::npos
                    || content->find("navuryx-ghost") != std::string::npos)) {
                return true;
            }
        }
        Sleep(350);
    }
    return DllInjectLogShowsSuccess();
}

bool CleanupLaunchArgs(const std::wstring& instancePath) {
    fs::path jvm = fs::path(instancePath) / L"user_jvm_args.txt";
    WriteFileUtf8(jvm, "");

    fs::path instanceJson = fs::path(instancePath) / L"minecraftinstance.json";
    auto json = ReadFileUtf8(instanceJson);
    if (json) {
        ReplaceJavaArgsOverride(*json, "");
        // set null properly
        std::regex pattern("\"javaArgsOverride\":(?:null|\"(?:\\\\.|[^\"])*\")");
        *json = std::regex_replace(*json, pattern, "\"javaArgsOverride\":null", std::regex_constants::format_first_only);
        WriteFileUtf8(instanceJson, *json);
    }

    fs::path cache = fs::path(AppData()) / L"CurseForge" / L"agent" / L"GameInstances" / L"MinecraftGameInstance.json";
    auto cacheJson = ReadFileUtf8(cache);
    if (cacheJson) {
        std::regex pattern("\"javaArgsOverride\":(?:null|\"(?:\\\\.|[^\"])*\")");
        *cacheJson = std::regex_replace(*cacheJson, pattern, "\"javaArgsOverride\":null");
        WriteFileUtf8(cache, *cacheJson);
    }

    fs::path mods = fs::path(instancePath) / L"mods";
    DeleteFileW((mods / L"navine-ghost-client.jar").c_str());
    DeleteFileW((mods / L"navuryx-ghost-client.jar").c_str());
    return true;
}

bool OpenFolder(const std::wstring& path) {
    if (path.empty() || GetFileAttributesW(path.c_str()) == INVALID_FILE_ATTRIBUTES) {
        return false;
    }
    return ShellOpen(path);
}

std::wstring FormatInstanceLabel(const std::wstring& instancePath) {
    if (instancePath.empty()) {
        return L"No instance detected";
    }
    fs::path p(instancePath);
    std::wstring label = p.filename().wstring();
    label += L"  ·  " + DetectLauncherName(instancePath);
    if (InstanceLooksLikeFabric(instancePath)) {
        label += L"  ·  Fabric";
    } else {
        label += L"  ·  not Fabric";
    }
    auto version = DetectGameVersion(instancePath);
    if (version) {
        label += L"  ·  MC " + *version;
    }
    return label;
}

#include "inject_runtime_detect.inc"

} // namespace ghost
