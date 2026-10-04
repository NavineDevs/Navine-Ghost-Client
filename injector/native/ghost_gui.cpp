#include <windows.h>
#include <windowsx.h>
#include <string>
#include <thread>
#include <atomic>
#include <optional>
#include <algorithm>

#include "inject_core.h"

#pragma comment(lib, "user32.lib")
#pragma comment(lib, "gdi32.lib")
#pragma comment(lib, "shell32.lib")
#pragma comment(lib, "advapi32.lib")
#pragma comment(lib, "msimg32.lib")

#if defined(GHOST_BRAND_NAVURYX)
#  ifndef GHOST_PRODUCT_NAME
#    define GHOST_PRODUCT_NAME L"NavuryxGhostInjector"
#  endif
#  ifndef GHOST_BRAND
#    define GHOST_BRAND L"NAVURYX"
#  endif
#  ifndef GHOST_TITLE
#    define GHOST_TITLE L"Navuryx Ghost Client Injector"
#  endif
#  ifndef GHOST_AGENT_CLASS
#    define GHOST_AGENT_CLASS "nv.navuryxghost.injector.GhostAgent"
#  endif
#  ifndef GHOST_RUNTIME_FOLDER
#    define GHOST_RUNTIME_FOLDER L"NavuryxGhost"
#  endif
#  ifndef GHOST_ATTACHER_CLASS
#    define GHOST_ATTACHER_CLASS "nv.navuryxghost.injector.AgentAttacher"
#  endif
#else
#  ifndef GHOST_PRODUCT_NAME
#    define GHOST_PRODUCT_NAME L"NavineGhostInjector"
#  endif
#  ifndef GHOST_BRAND
#    define GHOST_BRAND L"NAVINE"
#  endif
#  ifndef GHOST_TITLE
#    define GHOST_TITLE L"Navine Ghost Client Injector"
#  endif
#  ifndef GHOST_AGENT_CLASS
#    define GHOST_AGENT_CLASS "nv.navineghost.injector.GhostAgent"
#  endif
#  ifndef GHOST_RUNTIME_FOLDER
#    define GHOST_RUNTIME_FOLDER L"NavineGhost"
#  endif
#  ifndef GHOST_ATTACHER_CLASS
#    define GHOST_ATTACHER_CLASS "nv.navineghost.injector.AgentAttacher"
#  endif
#endif

#if defined(GHOST_BUILD_VANILLA)
#  ifndef GHOST_FORCE_LOADER
#    define GHOST_FORCE_LOADER L"vanilla"
#  endif
#elif defined(GHOST_BUILD_FABRIC)
#  ifndef GHOST_FORCE_LOADER
#    define GHOST_FORCE_LOADER L"fabric"
#  endif
#else
#  ifndef GHOST_FORCE_LOADER
#    define GHOST_FORCE_LOADER L""
#  endif
#endif

#define IDR_AGENT 101
#define IDR_PAYLOAD 102
#define IDR_DLL 103
#define IDR_PAYLOAD_VANILLA 104
#define WM_DONE (WM_APP + 20)

static const wchar_t* kWindowClass = GHOST_PRODUCT_NAME L"Frame";
static HWND g_hwnd = nullptr;
static HFONT g_font = nullptr;
static HFONT g_fontTitle = nullptr;
static HFONT g_fontBtn = nullptr;
static std::atomic_bool g_busy{false};
static std::wstring g_status = L"";
static int g_statusKind = 0;
static int g_versionIndex = 6;
static int g_loaderIndex = 0;
static bool g_pickerOpen = false;
static bool g_loaderPickerOpen = false;
static bool g_hoverInject = false;
static bool g_hoverMin = false;
static bool g_hoverClose = false;
static bool g_hoverPicker = false;
static bool g_hoverLoaderPicker = false;
static int g_hoverVersion = -1;
static int g_hoverLoader = -1;
static bool g_mcLive = false;
static bool g_mcInWorld = false;
static std::wstring g_liveText = L"Minecraft not detected";
static RECT g_rcTitle{};
static RECT g_rcMin{};
static RECT g_rcClose{};
static RECT g_rcPicker{};
static RECT g_rcLoaderPicker{};
static RECT g_rcMenu{};
static RECT g_rcLoaderMenu{};
static RECT g_rcInject{};
static RECT g_rcDrag{};
static RECT g_rcStatus{};
static RECT g_rcLive{};

static const wchar_t* kVersions[] = {
        L"1.8.9", L"1.12.2", L"1.21.11", L"1.21.4",
        L"26", L"26.1", L"26.1.2", L"26.2", L"26.3", L"26.4", L"26.5"
};
static const int kVersionCount = 11;

static const wchar_t* kLoaders[] = {
        L"Auto Detect", L"Fabric", L"Vanilla / Forge"
};
static const int kLoaderCount = 3;

static std::wstring ToLowerCopy(std::wstring value) {
    std::transform(value.begin(), value.end(), value.begin(), ::towlower);
    return value;
}

static int MatchVersionIndex(const std::wstring& detected) {
    if (detected.empty()) {
        return -1;
    }
    std::wstring lower = ToLowerCopy(detected);
    int bestIdx = -1;
    int bestLen = 0;
    for (int i = 0; i < kVersionCount; i++) {
        std::wstring candidate = ToLowerCopy(kVersions[i]);
        if (lower == candidate || lower.find(candidate) != std::wstring::npos || candidate.find(lower) != std::wstring::npos) {
            int len = static_cast<int>(candidate.length());
            if (len > bestLen) {
                bestLen = len;
                bestIdx = i;
            }
        }
    }
    return bestIdx;
}

static bool RefreshAutoSelections() {
    auto instance = ghost::FindPreferredInstancePath(L"");
    if (!instance) {
        return false;
    }
    bool changed = false;
    auto version = ghost::DetectGameVersion(*instance);
    if (version) {
        int idx = MatchVersionIndex(*version);
        if (idx >= 0 && idx != g_versionIndex) {
            g_versionIndex = idx;
            changed = true;
        }
    }
    int nextLoader = g_loaderIndex;
    if (ghost::InstanceLooksLikeFabric(*instance)) {
        nextLoader = 1;
    } else if (ghost::InstanceLooksLikeForge(*instance) || ghost::DetectLoaderLabel(*instance) == L"Vanilla") {
        nextLoader = 2;
    }
    if (nextLoader != g_loaderIndex) {
        g_loaderIndex = nextLoader;
        changed = true;
    }
    return changed;
}

static COLORREF CBg() { return RGB(14, 16, 20); }
static COLORREF CField() { return RGB(20, 22, 28); }
static COLORREF CLine() { return RGB(40, 44, 54); }
static COLORREF CText() { return RGB(240, 242, 246); }
static COLORREF CMuted() { return RGB(140, 146, 158); }
static COLORREF CAccent() { return RGB(62, 152, 214); }
static COLORREF CAccentSoft() { return RGB(32, 64, 96); }
static COLORREF CAccentH() { return RGB(92, 176, 236); }
static COLORREF COk() { return RGB(72, 190, 128); }
static COLORREF CBad() { return RGB(220, 88, 88); }
static COLORREF CDanger() { return RGB(168, 48, 48); }

static std::wstring Heading() {
    return std::wstring(GHOST_BRAND) + L" Ghost Client Injector";
}

static std::wstring SelectedLoaderWide() {
#if defined(GHOST_BUILD_VANILLA)
    return L"vanilla";
#elif defined(GHOST_BUILD_FABRIC)
    return L"fabric";
#else
    if (g_loaderIndex == 1) {
        return L"fabric";
    }
    if (g_loaderIndex == 2) {
        return L"vanilla";
    }
    return L"";
#endif
}

static std::string SelectedLoaderUtf8() {
    return ghost::Narrow(SelectedLoaderWide());
}

static void ApplyLoaderEnv() {
    std::wstring loaderForce = SelectedLoaderWide();
    if (!loaderForce.empty()) {
        SetEnvironmentVariableW(L"GHOST_FORCE_LOADER", loaderForce.c_str());
    } else {
        SetEnvironmentVariableW(L"GHOST_FORCE_LOADER", nullptr);
    }
}

static ghost::RuntimePaths BuildRuntimePaths() {
    ghost::RuntimePaths paths;
    static std::wstring session = std::to_wstring(GetTickCount64());
    paths.root = ghost::LocalAppData() + L"\\" + GHOST_RUNTIME_FOLDER + L"\\runtime\\" + session;
    paths.agent = paths.root + L"\\agent.jar";
    paths.payload = paths.root + L"\\payload.jar";
    paths.payloadVanilla = paths.root + L"\\payload-vanilla.jar";
    paths.dll = paths.root + L"\\bridge.dll";
    paths.cfg = ghost::TempPath() + L"ghost-java-inject.cfg";
    return paths;
}

static bool EnsureRuntimeFiles(std::wstring& error) {
    auto paths = BuildRuntimePaths();
    ghost::EnsureDir(paths.root);
    HMODULE module = GetModuleHandleW(nullptr);

    auto forceExtract = [&](int resId, const std::wstring& target) -> bool {
        SetFileAttributesW(target.c_str(), FILE_ATTRIBUTE_NORMAL);
        DeleteFileW(target.c_str());
        if (ghost::ExtractResource(module, resId, target)) {
            return GetFileAttributesW(target.c_str()) != INVALID_FILE_ATTRIBUTES;
        }
        return false;
    };

    if (!forceExtract(IDR_AGENT, paths.agent)) {
        error = L"Missing agent package inside EXE.";
        return false;
    }
    if (!forceExtract(IDR_PAYLOAD, paths.payload)) {
        CopyFileW(paths.agent.c_str(), paths.payload.c_str(), FALSE);
        if (GetFileAttributesW(paths.payload.c_str()) == INVALID_FILE_ATTRIBUTES) {
            error = L"Missing client package inside EXE.";
            return false;
        }
    }
    if (!forceExtract(IDR_PAYLOAD_VANILLA, paths.payloadVanilla)) {
        CopyFileW(paths.payload.c_str(), paths.payloadVanilla.c_str(), FALSE);
    }
    if (!forceExtract(IDR_DLL, paths.dll)) {
        error = L"Missing inject bridge inside EXE.";
        return false;
    }
    if (!ghost::WriteInjectConfig(paths, GHOST_AGENT_CLASS, SelectedLoaderUtf8())) {
        error = L"Could not write inject config.";
        return false;
    }
    return true;
}

static void Layout(int w, int h) {
    g_rcTitle = {0, 0, w, 40};
    g_rcDrag = {0, 0, w - 80, 40};
    g_rcMin = {w - 80, 0, w - 40, 40};
    g_rcClose = {w - 40, 0, w, 40};
    g_rcLive = {20, 52, w - 20, 76};
    g_rcPicker = {20, 88, w - 20, 124};
    g_rcLoaderPicker = {20, 132, w - 20, 168};
    g_rcInject = {20, 184, w - 20, 234};
    g_rcStatus = {20, 244, w - 20, 280};
    const int itemH = 26;
    const int menuH = 8 + kVersionCount * itemH;
    const int loaderMenuH = 8 + kLoaderCount * itemH;
    g_rcMenu = {20, g_rcPicker.bottom + 4, w - 20, g_rcPicker.bottom + 4 + menuH};
    g_rcLoaderMenu = {20, g_rcLoaderPicker.bottom + 4, w - 20, g_rcLoaderPicker.bottom + 4 + loaderMenuH};
    (void)h;
}

static void SetStatus(const std::wstring& text, int kind) {
    g_status = text;
    g_statusKind = kind;
    if (g_hwnd) {
        InvalidateRect(g_hwnd, nullptr, FALSE);
    }
}

static void DrawTextRect(HDC hdc, RECT rc, const wchar_t* text, HFONT font, COLORREF color, UINT format) {
    HGDIOBJ old = SelectObject(hdc, font);
    SetTextColor(hdc, color);
    SetBkMode(hdc, TRANSPARENT);
    DrawTextW(hdc, text, -1, &rc, format);
    SelectObject(hdc, old);
}

static void FillRound(HDC hdc, RECT rc, COLORREF fill, COLORREF border, int radius) {
    HBRUSH brush = CreateSolidBrush(fill);
    HPEN pen = CreatePen(PS_SOLID, 1, border);
    HGDIOBJ oldB = SelectObject(hdc, brush);
    HGDIOBJ oldP = SelectObject(hdc, pen);
    RoundRect(hdc, rc.left, rc.top, rc.right, rc.bottom, radius, radius);
    SelectObject(hdc, oldB);
    SelectObject(hdc, oldP);
    DeleteObject(brush);
    DeleteObject(pen);
}

static void DrawLine(HDC hdc, int x1, int y1, int x2, int y2, COLORREF color, int width) {
    HPEN pen = CreatePen(PS_SOLID, width, color);
    HGDIOBJ old = SelectObject(hdc, pen);
    MoveToEx(hdc, x1, y1, nullptr);
    LineTo(hdc, x2, y2);
    SelectObject(hdc, old);
    DeleteObject(pen);
}

static void DrawMinIcon(HDC hdc, RECT rc, COLORREF color) {
    int cx = (rc.left + rc.right) / 2;
    int cy = (rc.top + rc.bottom) / 2;
    DrawLine(hdc, cx - 5, cy, cx + 6, cy, color, 2);
}

static void DrawCloseIcon(HDC hdc, RECT rc, COLORREF color) {
    int cx = (rc.left + rc.right) / 2;
    int cy = (rc.top + rc.bottom) / 2;
    DrawLine(hdc, cx - 5, cy - 5, cx + 6, cy + 6, color, 2);
    DrawLine(hdc, cx + 5, cy - 5, cx - 6, cy + 6, color, 2);
}

static void DrawChevron(HDC hdc, RECT rc, bool up, COLORREF color) {
    int cx = (rc.left + rc.right) / 2;
    int cy = (rc.top + rc.bottom) / 2;
    POINT pts[3];
    if (up) {
        pts[0] = {cx - 5, cy + 2};
        pts[1] = {cx + 5, cy + 2};
        pts[2] = {cx, cy - 3};
    } else {
        pts[0] = {cx - 5, cy - 2};
        pts[1] = {cx + 5, cy - 2};
        pts[2] = {cx, cy + 3};
    }
    HBRUSH brush = CreateSolidBrush(color);
    HPEN pen = CreatePen(PS_SOLID, 1, color);
    HGDIOBJ oldB = SelectObject(hdc, brush);
    HGDIOBJ oldP = SelectObject(hdc, pen);
    Polygon(hdc, pts, 3);
    SelectObject(hdc, oldB);
    SelectObject(hdc, oldP);
    DeleteObject(brush);
    DeleteObject(pen);
}

static void Paint(HWND hwnd) {
    PAINTSTRUCT ps{};
    HDC hdc = BeginPaint(hwnd, &ps);
    RECT rc{};
    GetClientRect(hwnd, &rc);

    HDC mem = CreateCompatibleDC(hdc);
    HBITMAP bmp = CreateCompatibleBitmap(hdc, rc.right, rc.bottom);
    HGDIOBJ oldBmp = SelectObject(mem, bmp);

    HBRUSH bg = CreateSolidBrush(CBg());
    FillRect(mem, &rc, bg);
    DeleteObject(bg);

    HBRUSH titleBrush = CreateSolidBrush(RGB(10, 12, 16));
    FillRect(mem, &g_rcTitle, titleBrush);
    DeleteObject(titleBrush);
    DrawLine(mem, 0, g_rcTitle.bottom - 1, rc.right, g_rcTitle.bottom - 1, CLine(), 1);

    std::wstring heading = Heading();
    DrawTextRect(mem, RECT{16, 0, g_rcDrag.right - 8, 40}, heading.c_str(), g_fontTitle,
            CText(), DT_LEFT | DT_VCENTER | DT_SINGLELINE | DT_END_ELLIPSIS);

    HBRUSH minBg = CreateSolidBrush(g_hoverMin ? RGB(28, 32, 40) : RGB(10, 12, 16));
    FillRect(mem, &g_rcMin, minBg);
    DeleteObject(minBg);
    DrawMinIcon(mem, g_rcMin, g_hoverMin ? CText() : CMuted());

    HBRUSH closeBg = CreateSolidBrush(g_hoverClose ? CDanger() : RGB(10, 12, 16));
    FillRect(mem, &g_rcClose, closeBg);
    DeleteObject(closeBg);
    DrawCloseIcon(mem, g_rcClose, g_hoverClose ? RGB(255, 255, 255) : CMuted());

    DrawTextRect(mem, g_rcLive, g_liveText.c_str(), g_font, g_mcLive ? COk() : CMuted(),
            DT_LEFT | DT_VCENTER | DT_SINGLELINE);

    FillRound(mem, g_rcPicker, CField(), g_hoverPicker || g_pickerOpen ? CAccent() : CLine(), 8);
    DrawTextRect(mem, RECT{g_rcPicker.left + 12, g_rcPicker.top, g_rcPicker.right - 36, g_rcPicker.bottom},
            kVersions[g_versionIndex], g_font, CText(), DT_LEFT | DT_VCENTER | DT_SINGLELINE);
    RECT chev{g_rcPicker.right - 32, g_rcPicker.top, g_rcPicker.right - 8, g_rcPicker.bottom};
    DrawChevron(mem, chev, g_pickerOpen, CMuted());

    FillRound(mem, g_rcLoaderPicker, CField(), g_hoverLoaderPicker || g_loaderPickerOpen ? CAccent() : CLine(), 8);
    DrawTextRect(mem, RECT{g_rcLoaderPicker.left + 12, g_rcLoaderPicker.top, g_rcLoaderPicker.right - 36, g_rcLoaderPicker.bottom},
            kLoaders[g_loaderIndex], g_font, CText(), DT_LEFT | DT_VCENTER | DT_SINGLELINE);
    RECT loaderChev{g_rcLoaderPicker.right - 32, g_rcLoaderPicker.top, g_rcLoaderPicker.right - 8, g_rcLoaderPicker.bottom};
    DrawChevron(mem, loaderChev, g_loaderPickerOpen, CMuted());

    COLORREF inj = g_busy ? CAccentSoft() : (g_hoverInject ? CAccentH() : CAccent());
    FillRound(mem, g_rcInject, inj, inj, 10);
    DrawTextRect(mem, g_rcInject, g_busy ? L"INJECTING..." : L"INJECT", g_fontBtn, RGB(6, 10, 14),
            DT_CENTER | DT_VCENTER | DT_SINGLELINE);

    if (!g_status.empty()) {
        COLORREF sc = g_statusKind == 1 ? COk() : (g_statusKind == 2 ? CBad() : CMuted());
        DrawTextRect(mem, g_rcStatus, g_status.c_str(), g_font, sc,
                DT_CENTER | DT_VCENTER | DT_SINGLELINE | DT_END_ELLIPSIS);
    }

    if (g_pickerOpen) {
        FillRound(mem, g_rcMenu, CField(), CLine(), 8);
        for (int i = 0; i < kVersionCount; i++) {
            RECT item{g_rcMenu.left + 4, g_rcMenu.top + 4 + i * 26, g_rcMenu.right - 4, g_rcMenu.top + 4 + (i + 1) * 26};
            if (i == g_versionIndex || i == g_hoverVersion) {
                FillRound(mem, item, RGB(28, 36, 48), RGB(28, 36, 48), 6);
            }
            RECT text = item;
            text.left += 10;
            DrawTextRect(mem, text, kVersions[i], g_font, CText(), DT_LEFT | DT_VCENTER | DT_SINGLELINE);
        }
    }

    if (g_loaderPickerOpen) {
        FillRound(mem, g_rcLoaderMenu, CField(), CLine(), 8);
        for (int i = 0; i < kLoaderCount; i++) {
            RECT item{g_rcLoaderMenu.left + 4, g_rcLoaderMenu.top + 4 + i * 26, g_rcLoaderMenu.right - 4, g_rcLoaderMenu.top + 4 + (i + 1) * 26};
            if (i == g_loaderIndex || i == g_hoverLoader) {
                FillRound(mem, item, RGB(28, 36, 48), RGB(28, 36, 48), 6);
            }
            RECT text = item;
            text.left += 10;
            DrawTextRect(mem, text, kLoaders[i], g_font, CText(), DT_LEFT | DT_VCENTER | DT_SINGLELINE);
        }
    }

    BitBlt(hdc, 0, 0, rc.right, rc.bottom, mem, 0, 0, SRCCOPY);
    SelectObject(mem, oldBmp);
    DeleteObject(bmp);
    DeleteDC(mem);
    EndPaint(hwnd, &ps);
}

static void RunInject(HWND hwnd) {
    if (g_busy.exchange(true)) {
        return;
    }
    SetStatus(L"Injecting...", 0);
    g_pickerOpen = false;
    g_loaderPickerOpen = false;
    InvalidateRect(hwnd, nullptr, FALSE);

    std::wstring version = kVersions[g_versionIndex];
    std::thread([hwnd, version]() {
        auto post = [hwnd](int kind, const std::wstring& text) {
            auto* msg = new std::wstring(text);
            PostMessageW(hwnd, WM_DONE, static_cast<WPARAM>(kind), reinterpret_cast<LPARAM>(msg));
        };

        std::wstring error;
        auto paths = BuildRuntimePaths();
        if (!EnsureRuntimeFiles(error)) {
            post(2, error);
            return;
        }

        auto instance = ghost::FindPreferredInstancePath(version);
        if (!instance) {
            instance = ghost::FindPreferredInstancePath(L"");
        }
        if (instance) {
            ghost::RemoveGhostInstallArtifacts(*instance);
        }

        ghost::EnableDebugPrivilege();
        ApplyLoaderEnv();
        ghost::WriteInjectConfig(paths, GHOST_AGENT_CLASS, SelectedLoaderUtf8());
        ghost::AppendNativeInjectLog("RunInject begin");
        DeleteFileW((ghost::TempPath() + L"ghost-java-inject.log").c_str());

        auto pid = ghost::FindMinecraftJavaPid();
        if (!pid) {
            ghost::AppendNativeInjectLog("Minecraft PID not found");
            post(2, L"Minecraft not detected");
            return;
        }
        ghost::AppendNativeInjectLog("Minecraft PID=" + std::to_string(*pid));

        post(0, L"Injecting...");
#ifndef GHOST_ATTACHER_CLASS
#define GHOST_ATTACHER_CLASS "nv.navineghost.injector.AgentAttacher"
#endif
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

        ghost::WaitForInjectEvidence(instance, 20000);
        ghost::AppendNativeInjectLog("attach=" + std::to_string(attached) + " dll=" + std::to_string(dllOk));
        bool clientUp = false;
        bool tickFired = false;
        DWORD waitStart = GetTickCount();
        while (GetTickCount() - waitStart < 25000) {
            auto logPath = ghost::TempPath() + L"ghost-java-inject.log";
            HANDLE h = CreateFileW(logPath.c_str(), GENERIC_READ, FILE_SHARE_READ | FILE_SHARE_WRITE,
                    nullptr, OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, nullptr);
            if (h != INVALID_HANDLE_VALUE) {
                DWORD size = GetFileSize(h, nullptr);
                if (size > 0 && size < 4 * 1024 * 1024) {
                    std::string body(size, '\0');
                    DWORD read = 0;
                    if (ReadFile(h, body.data(), size, &read, nullptr)) {
                        body.resize(read);
                        clientUp = body.find("clientStarted=true") != std::string::npos
                                || body.find("client bootstrapped") != std::string::npos;
                        tickFired = body.find("tickFired=true") != std::string::npos;
                        if (tickFired || body.find("ghost done") != std::string::npos) {
                            CloseHandle(h);
                            break;
                        }
                    }
                }
                CloseHandle(h);
            }
            Sleep(200);
        }

        if (clientUp || tickFired) {
            post(1, L"Injected");
            return;
        }
        if (dllOk && attached) {
            post(1, L"Injected");
            return;
        }
        post(2, L"Inject failed");
    }).detach();
}

static LRESULT CALLBACK WndProc(HWND hwnd, UINT msg, WPARAM wParam, LPARAM lParam) {
    switch (msg) {
    case WM_CREATE: {
        g_font = CreateFontW(15, 0, 0, 0, FW_NORMAL, FALSE, FALSE, FALSE, DEFAULT_CHARSET,
                OUT_DEFAULT_PRECIS, CLIP_DEFAULT_PRECIS, CLEARTYPE_QUALITY, DEFAULT_PITCH | FF_SWISS, L"Segoe UI");
        g_fontTitle = CreateFontW(14, 0, 0, 0, FW_SEMIBOLD, FALSE, FALSE, FALSE, DEFAULT_CHARSET,
                OUT_DEFAULT_PRECIS, CLIP_DEFAULT_PRECIS, CLEARTYPE_QUALITY, DEFAULT_PITCH | FF_SWISS, L"Segoe UI");
        g_fontBtn = CreateFontW(15, 0, 0, 0, FW_BOLD, FALSE, FALSE, FALSE, DEFAULT_CHARSET,
                OUT_DEFAULT_PRECIS, CLIP_DEFAULT_PRECIS, CLEARTYPE_QUALITY, DEFAULT_PITCH | FF_SWISS, L"Segoe UI");
        RECT rc{};
        GetClientRect(hwnd, &rc);
        Layout(rc.right, rc.bottom);
        RefreshAutoSelections();
        SetTimer(hwnd, 1, 1000, nullptr);
        return 0;
    }
    case WM_TIMER:
        if (wParam == 1) {
            auto pid = ghost::FindMinecraftJavaPid();
            bool live = pid.has_value();
            bool inWorld = false;
            std::wstring nextLive = L"Minecraft not detected";
            if (live && pid) {
                inWorld = ghost::IsMinecraftInWorld(*pid);
                std::wstring detectedVersion;
                std::wstring detectedLoader = ghost::DetectRunningLoaderLabel(*pid);
                if (RefreshAutoSelections()) {
                    InvalidateRect(hwnd, nullptr, FALSE);
                }
                auto instance = ghost::FindPreferredInstancePath(kVersions[g_versionIndex]);
                if (!instance) {
                    instance = ghost::FindPreferredInstancePath(L"");
                }
                auto runningVersion = ghost::DetectRunningMinecraftVersion(*pid);
                if (runningVersion && !runningVersion->empty()) {
                    detectedVersion = *runningVersion;
                } else if (instance) {
                    auto version = ghost::DetectGameVersion(*instance);
                    if (version && !version->empty()) {
                        detectedVersion = *version;
                    }
                }
                if (detectedLoader.empty() && instance) {
                    detectedLoader = ghost::DetectLoaderLabel(*instance);
                }
                if (!detectedVersion.empty()) {
                    nextLive = detectedVersion;
                } else {
                    nextLive = L"Unknown version";
                }
                if (!detectedLoader.empty()) {
                    nextLive += L" · ";
                    nextLive += detectedLoader;
                }
            }
            if (live != g_mcLive || nextLive != g_liveText) {
                g_mcLive = live;
                g_liveText = nextLive;
                InvalidateRect(hwnd, nullptr, FALSE);
            }
        }
        return 0;
    case WM_SIZE:
        Layout(LOWORD(lParam), HIWORD(lParam));
        InvalidateRect(hwnd, nullptr, FALSE);
        return 0;
    case WM_ERASEBKGND:
        return 1;
    case WM_PAINT:
        Paint(hwnd);
        return 0;
    case WM_NCHITTEST: {
        LRESULT hit = DefWindowProcW(hwnd, msg, wParam, lParam);
        if (hit == HTCLIENT) {
            POINT p{GET_X_LPARAM(lParam), GET_Y_LPARAM(lParam)};
            ScreenToClient(hwnd, &p);
            if (PtInRect(&g_rcDrag, p)
                    && !PtInRect(&g_rcMin, p)
                    && !PtInRect(&g_rcClose, p)
                    && !PtInRect(&g_rcPicker, p)
                    && !PtInRect(&g_rcLoaderPicker, p)
                    && !PtInRect(&g_rcInject, p)
                    && !(g_pickerOpen && PtInRect(&g_rcMenu, p))
                    && !(g_loaderPickerOpen && PtInRect(&g_rcLoaderMenu, p))) {
                return HTCAPTION;
            }
        }
        return hit;
    }
    case WM_MOUSEMOVE: {
        POINT p{GET_X_LPARAM(lParam), GET_Y_LPARAM(lParam)};
        bool changed = false;
        auto set = [&](bool& flag, bool value) {
            if (flag != value) {
                flag = value;
                changed = true;
            }
        };
        set(g_hoverMin, PtInRect(&g_rcMin, p) != 0);
        set(g_hoverClose, PtInRect(&g_rcClose, p) != 0);
        set(g_hoverPicker, PtInRect(&g_rcPicker, p) != 0);
        set(g_hoverLoaderPicker, PtInRect(&g_rcLoaderPicker, p) != 0);
        set(g_hoverInject, !g_busy && PtInRect(&g_rcInject, p));
        int nextHover = -1;
        if (g_pickerOpen) {
            for (int i = 0; i < kVersionCount; i++) {
                RECT item{g_rcMenu.left + 4, g_rcMenu.top + 4 + i * 26, g_rcMenu.right - 4, g_rcMenu.top + 4 + (i + 1) * 26};
                if (PtInRect(&item, p)) {
                    nextHover = i;
                    break;
                }
            }
        }
        if (g_hoverVersion != nextHover) {
            g_hoverVersion = nextHover;
            changed = true;
        }
        int nextLoaderHover = -1;
        if (g_loaderPickerOpen) {
            for (int i = 0; i < kLoaderCount; i++) {
                RECT item{g_rcLoaderMenu.left + 4, g_rcLoaderMenu.top + 4 + i * 26, g_rcLoaderMenu.right - 4, g_rcLoaderMenu.top + 4 + (i + 1) * 26};
                if (PtInRect(&item, p)) {
                    nextLoaderHover = i;
                    break;
                }
            }
        }
        if (g_hoverLoader != nextLoaderHover) {
            g_hoverLoader = nextLoaderHover;
            changed = true;
        }
        TRACKMOUSEEVENT tme{sizeof(tme), TME_LEAVE, hwnd, 0};
        TrackMouseEvent(&tme);
        if (changed) {
            InvalidateRect(hwnd, nullptr, FALSE);
        }
        return 0;
    }
    case WM_MOUSELEAVE:
        g_hoverInject = g_hoverMin = g_hoverClose = g_hoverPicker = g_hoverLoaderPicker = false;
        g_hoverVersion = -1;
        g_hoverLoader = -1;
        InvalidateRect(hwnd, nullptr, FALSE);
        return 0;
    case WM_LBUTTONUP: {
        POINT p{GET_X_LPARAM(lParam), GET_Y_LPARAM(lParam)};
        if (PtInRect(&g_rcClose, p)) {
            DestroyWindow(hwnd);
            return 0;
        }
        if (PtInRect(&g_rcMin, p)) {
            ShowWindow(hwnd, SW_MINIMIZE);
            return 0;
        }
        if (g_pickerOpen) {
            for (int i = 0; i < kVersionCount; i++) {
                RECT item{g_rcMenu.left + 4, g_rcMenu.top + 4 + i * 26, g_rcMenu.right - 4, g_rcMenu.top + 4 + (i + 1) * 26};
                if (PtInRect(&item, p)) {
                    g_versionIndex = i;
                    g_pickerOpen = false;
                    InvalidateRect(hwnd, nullptr, FALSE);
                    return 0;
                }
            }
            if (!PtInRect(&g_rcPicker, p)) {
                g_pickerOpen = false;
                InvalidateRect(hwnd, nullptr, FALSE);
                return 0;
            }
        }
        if (g_loaderPickerOpen) {
            for (int i = 0; i < kLoaderCount; i++) {
                RECT item{g_rcLoaderMenu.left + 4, g_rcLoaderMenu.top + 4 + i * 26, g_rcLoaderMenu.right - 4, g_rcLoaderMenu.top + 4 + (i + 1) * 26};
                if (PtInRect(&item, p)) {
                    g_loaderIndex = i;
                    g_loaderPickerOpen = false;
                    InvalidateRect(hwnd, nullptr, FALSE);
                    return 0;
                }
            }
            if (!PtInRect(&g_rcLoaderPicker, p)) {
                g_loaderPickerOpen = false;
                InvalidateRect(hwnd, nullptr, FALSE);
                return 0;
            }
        }
        if (PtInRect(&g_rcPicker, p)) {
            g_pickerOpen = !g_pickerOpen;
            g_loaderPickerOpen = false;
            InvalidateRect(hwnd, nullptr, FALSE);
            return 0;
        }
        if (PtInRect(&g_rcLoaderPicker, p)) {
            g_loaderPickerOpen = !g_loaderPickerOpen;
            g_pickerOpen = false;
            InvalidateRect(hwnd, nullptr, FALSE);
            return 0;
        }
        if (!g_busy && PtInRect(&g_rcInject, p)) {
            RunInject(hwnd);
            return 0;
        }
        return 0;
    }
    case WM_DONE: {
        auto* text = reinterpret_cast<std::wstring*>(lParam);
        if (text) {
            if (wParam == 0) {
                SetStatus(*text, 0);
                g_busy = true;
            } else {
                SetStatus(*text, wParam == 1 ? 1 : 2);
                g_busy = false;
            }
            delete text;
        } else if (wParam != 0) {
            g_busy = false;
        }
        InvalidateRect(hwnd, nullptr, FALSE);
        return 0;
    }
    case WM_DESTROY:
        KillTimer(hwnd, 1);
        if (g_font) DeleteObject(g_font);
        if (g_fontTitle) DeleteObject(g_fontTitle);
        if (g_fontBtn) DeleteObject(g_fontBtn);
        PostQuitMessage(0);
        return 0;
    default:
        return DefWindowProcW(hwnd, msg, wParam, lParam);
    }
}

int WINAPI wWinMain(HINSTANCE instance, HINSTANCE, PWSTR, int show) {
    WNDCLASSEXW wc{sizeof(wc)};
    wc.lpfnWndProc = WndProc;
    wc.hInstance = instance;
    wc.lpszClassName = kWindowClass;
    wc.hCursor = LoadCursor(nullptr, IDC_ARROW);
    wc.hbrBackground = reinterpret_cast<HBRUSH>(GetStockObject(BLACK_BRUSH));
    wc.hIcon = LoadIcon(nullptr, IDI_APPLICATION);
    RegisterClassExW(&wc);

    const int width = 400;
    const int height = 360;
    g_hwnd = CreateWindowExW(
            WS_EX_APPWINDOW,
            kWindowClass,
            Heading().c_str(),
            WS_POPUP | WS_MINIMIZEBOX | WS_SYSMENU | WS_CLIPCHILDREN,
            CW_USEDEFAULT, CW_USEDEFAULT, width, height,
            nullptr, nullptr, instance, nullptr);
    if (!g_hwnd) {
        MessageBoxW(nullptr, L"Failed to create window.", GHOST_TITLE, MB_ICONERROR);
        return 1;
    }

    RECT work{};
    SystemParametersInfoW(SPI_GETWORKAREA, 0, &work, 0);
    int x = work.left + ((work.right - work.left) - width) / 2;
    int y = work.top + ((work.bottom - work.top) - height) / 2;
    SetWindowPos(g_hwnd, nullptr, x, y, width, height, SWP_NOZORDER);

    HRGN rgn = CreateRoundRectRgn(0, 0, width + 1, height + 1, 12, 12);
    SetWindowRgn(g_hwnd, rgn, TRUE);

    ShowWindow(g_hwnd, show);
    UpdateWindow(g_hwnd);

    MSG msg{};
    while (GetMessageW(&msg, nullptr, 0, 0) > 0) {
        TranslateMessage(&msg);
        DispatchMessageW(&msg);
    }
    return static_cast<int>(msg.wParam);
}
