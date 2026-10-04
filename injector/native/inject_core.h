#pragma once
#include <windows.h>
#include <string>
#include <vector>
#include <optional>

namespace ghost {

struct RuntimePaths {
    std::wstring root;
    std::wstring agent;
    std::wstring payload;
    std::wstring payloadVanilla;
    std::wstring dll;
    std::wstring cfg;
};

std::wstring LocalAppData();
std::wstring AppData();
std::wstring TempPath();
bool EnsureDir(const std::wstring& path);
bool WriteFileBytes(const std::wstring& path, const void* data, size_t size);
bool ExtractResource(HMODULE module, int resId, const std::wstring& outPath);
bool WriteInjectConfig(const RuntimePaths& paths, const std::string& agentClass);
bool WriteInjectConfig(const RuntimePaths& paths, const std::string& agentClass, const std::string& forceLoader);
bool EnableDebugPrivilege();
void AppendNativeInjectLog(const std::string& line);
bool InjectDll(DWORD pid, const std::wstring& dllPath);
bool InjectDllWithRetries(DWORD pid, const std::wstring& dllPath, int attempts = 8, DWORD delayMs = 700);
bool AttachJavaAgent(DWORD pid, const RuntimePaths& paths, const std::string& attacherClass);
std::wstring FindJavaHomeExe(DWORD minecraftPid = 0);
std::optional<DWORD> FindMinecraftJavaPid();
std::optional<DWORD> WaitForMinecraftJavaPid(DWORD timeoutMs);
bool WaitForInjectEvidence(const std::optional<std::wstring>& instancePath, DWORD timeoutMs);
void KillMinecraftJava();
std::wstring GetExeDir();
bool ShellOpen(const std::wstring& pathOrUri);
std::optional<std::wstring> FindPreferredInstanceGuid(const std::wstring& preferredVersion = L"");
std::optional<std::wstring> FindPreferredInstancePath(const std::wstring& preferredVersion = L"");
std::optional<std::wstring> DetectGameVersion(const std::wstring& instancePath);
bool IsSupportedGameVersion(const std::wstring& version);
bool LaunchCurseForgeInstance(const std::wstring& guid);
bool LaunchMinecraftAny(const std::wstring& instancePath);
bool InstanceLooksLikeFabric(const std::wstring& instancePath);
bool InstanceLooksLikeForge(const std::wstring& instancePath);
std::wstring DetectLoaderLabel(const std::wstring& instancePath);
bool InstallLaunchArgs(const RuntimePaths& paths, const std::wstring& instancePath);
bool CleanupLaunchArgs(const std::wstring& instancePath);
bool DeployStealthPayload(const RuntimePaths& paths, std::wstring& stealthPayloadOut);
bool DeployModsFolderFallback(const RuntimePaths& paths, const std::wstring& instancePath);
int RemoveGhostInstallArtifacts(const std::wstring& instancePath);
bool WaitForGhostInLog(const std::wstring& instancePath, DWORD timeoutMs);
bool OpenFolder(const std::wstring& path);
std::wstring FormatInstanceLabel(const std::wstring& instancePath);
std::wstring DetectLauncherName(const std::wstring& instancePath);
std::optional<std::wstring> DetectRunningMinecraftVersion(DWORD pid);
std::wstring DetectRunningLoaderLabel(DWORD pid);
bool IsMinecraftInWorld(DWORD pid);
bool IsMinecraftInWorldForInstance(const std::wstring& instancePath);

std::string EscapeJson(const std::string& value);
std::string Narrow(const std::wstring& wide);
std::wstring Widen(const std::string& utf8);

} // namespace ghost
