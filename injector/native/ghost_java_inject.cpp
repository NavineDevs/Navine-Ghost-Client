#include <windows.h>
#include <jni.h>
#include <string>
#include <vector>
#include <fstream>
#include <sstream>
#include <filesystem>

#pragma comment(lib, "user32.lib")

namespace {

constexpr wchar_t kConfigPathEnv[] = L"GHOST_JAVA_INJECT_CFG";
constexpr char kLogName[] = "ghost-java-inject.log";

void Log(const char* msg) {
    char temp[MAX_PATH] = {};
    if (GetTempPathA(MAX_PATH, temp) == 0) {
        return;
    }
    std::string path = std::string(temp) + kLogName;
    std::ofstream out(path, std::ios::app);
    if (out) {
        out << msg << "\n";
    }
}

std::wstring ReadConfigPath() {
    wchar_t buf[MAX_PATH * 4] = {};
    DWORD n = GetEnvironmentVariableW(kConfigPathEnv, buf, MAX_PATH * 4);
    if (n > 0 && n < MAX_PATH * 4) {
        return buf;
    }
    wchar_t temp[MAX_PATH] = {};
    if (GetTempPathW(MAX_PATH, temp) == 0) {
        return L"";
    }
    return std::wstring(temp) + L"ghost-java-inject.cfg";
}

bool ReadConfig(std::string& agentJar, std::string& payloadJar, std::string& agentClass,
                std::string& payloadVanilla, std::string& forceLoader) {
    std::wstring wpath = ReadConfigPath();
    if (wpath.empty()) {
        return false;
    }
    std::ifstream in{std::filesystem::path(wpath)};
    if (!in) {
        Log("config open failed");
        return false;
    }
    if (!std::getline(in, agentJar)) {
        return false;
    }
    if (!std::getline(in, payloadJar)) {
        payloadJar.clear();
    }
    if (!std::getline(in, agentClass) || agentClass.empty()) {
        agentClass = "nv.navineghost.injector.GhostAgent";
    }
    if (!std::getline(in, payloadVanilla)) {
        payloadVanilla.clear();
    }
    if (!std::getline(in, forceLoader)) {
        forceLoader.clear();
    }
    auto trim = [](std::string& s) {
        while (!s.empty() && (s.back() == '\r' || s.back() == '\n')) {
            s.pop_back();
        }
    };
    trim(agentJar);
    trim(payloadJar);
    trim(agentClass);
    trim(payloadVanilla);
    trim(forceLoader);
    return !agentJar.empty();
}

typedef jint(JNICALL* GetCreatedJavaVMs)(JavaVM**, jsize, jsize*);

HMODULE WaitForJvm(DWORD timeoutMs) {
    DWORD start = GetTickCount();
    while (GetTickCount() - start < timeoutMs) {
        HMODULE jvm = GetModuleHandleW(L"jvm.dll");
        if (jvm) {
            return jvm;
        }
        Sleep(25);
    }
    return nullptr;
}

bool SetLoaderProps(JNIEnv* env, const std::string& payloadFabric, const std::string& payloadVanilla,
                    const std::string& forceLoader) {
    const std::string& primary = !payloadFabric.empty() ? payloadFabric : payloadVanilla;
    if (primary.empty()) {
        return false;
    }
    jclass systemClass = env->FindClass("java/lang/System");
    if (!systemClass) {
        env->ExceptionClear();
        Log("System class missing");
        return false;
    }
    jmethodID getProp = env->GetStaticMethodID(systemClass, "getProperty", "(Ljava/lang/String;)Ljava/lang/String;");
    jmethodID setProp = env->GetStaticMethodID(systemClass, "setProperty", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;");
    if (!getProp || !setProp) {
        env->ExceptionClear();
        Log("setProperty missing");
        return false;
    }

    auto setExact = [&](const char* keyName, const char* value) {
        jstring key = env->NewStringUTF(keyName);
        jstring val = env->NewStringUTF(value);
        env->CallStaticObjectMethod(systemClass, setProp, key, val);
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            return false;
        }
        return true;
    };

    auto appendProp = [&](const char* keyName, const std::string& payloadPath) {
        if (payloadPath.empty()) {
            return true;
        }
        jstring key = env->NewStringUTF(keyName);
        auto existingObj = reinterpret_cast<jstring>(env->CallStaticObjectMethod(systemClass, getProp, key));
        std::string combined = payloadPath;
        if (existingObj) {
            const char* existingChars = env->GetStringUTFChars(existingObj, nullptr);
            if (existingChars && existingChars[0] != '\0') {
                std::string existing(existingChars);
                if (existing.find(payloadPath) == std::string::npos) {
                    if (existing.find(';') != std::string::npos) {
                        combined = existing + ";" + payloadPath;
                    } else if (existing.find(':') != std::string::npos && existing.size() > 2 && existing[1] != ':') {
                        combined = existing + ":" + payloadPath;
                    } else {
                        combined = existing + ";" + payloadPath;
                    }
                } else {
                    combined = existing;
                }
            }
            if (existingChars) {
                env->ReleaseStringUTFChars(existingObj, existingChars);
            }
        }
        return setExact(keyName, combined.c_str());
    };

    bool forceVanilla = forceLoader == "vanilla" || forceLoader == "v";
    bool forceFabric = forceLoader == "fabric" || forceLoader == "f";
    bool ok = true;
    ok = setExact("ghost.payload", primary.c_str()) && ok;
    ok = setExact("ghost.injected", "true") && ok;
    if (!forceLoader.empty()) {
        ok = setExact("ghost.force.loader", forceLoader.c_str()) && ok;
    }
    if (!payloadFabric.empty() && !forceVanilla) {
        ok = setExact("ghost.payload.fabric", payloadFabric.c_str()) && ok;
        ok = appendProp("fabric.addMods", payloadFabric) && ok;
        ok = appendProp("fml.modLists", payloadFabric) && ok;
    }
    if (!payloadVanilla.empty()) {
        ok = setExact("ghost.payload.vanilla", payloadVanilla.c_str()) && ok;
    }
    if (forceVanilla && !payloadVanilla.empty()) {
        ok = setExact("ghost.payload", payloadVanilla.c_str()) && ok;
    } else if (forceFabric && !payloadFabric.empty()) {
        ok = setExact("ghost.payload", payloadFabric.c_str()) && ok;
    }
    (void)forceFabric;
    Log(ok ? "loader props set via injected DLL" : "some props failed");
    return ok;
}

bool InvokeGhostAgent(JNIEnv* env, const std::string& agentJar, const std::string& payloadPath, const std::string& agentClassName) {
    jclass fileClass = env->FindClass("java/io/File");
    jclass uriClass = env->FindClass("java/net/URI");
    jclass urlClass = env->FindClass("java/net/URL");
    jclass urlClClass = env->FindClass("java/net/URLClassLoader");
    jclass classClass = env->FindClass("java/lang/Class");
    jclass threadClass = env->FindClass("java/lang/Thread");
    if (!fileClass || !uriClass || !urlClass || !urlClClass || !classClass || !threadClass) {
        env->ExceptionClear();
        Log("core jni classes missing");
        return false;
    }

    jmethodID fileCtor = env->GetMethodID(fileClass, "<init>", "(Ljava/lang/String;)V");
    jmethodID toUri = env->GetMethodID(fileClass, "toURI", "()Ljava/net/URI;");
    jmethodID toUrl = env->GetMethodID(uriClass, "toURL", "()Ljava/net/URL;");
    jmethodID urlClCtor = env->GetMethodID(urlClClass, "<init>", "([Ljava/net/URL;Ljava/lang/ClassLoader;)V");
    jmethodID loadClass = env->GetMethodID(urlClClass, "loadClass", "(Ljava/lang/String;)Ljava/lang/Class;");
    jmethodID currentThread = env->GetStaticMethodID(threadClass, "currentThread", "()Ljava/lang/Thread;");
    jmethodID getContextCl = env->GetMethodID(threadClass, "getContextClassLoader", "()Ljava/lang/ClassLoader;");
    jmethodID getDeclaredMethod = env->GetMethodID(classClass, "getDeclaredMethod", "(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;");
    jclass methodClass = env->FindClass("java/lang/reflect/Method");
    jmethodID setAccessible = env->GetMethodID(methodClass, "setAccessible", "(Z)V");
    jmethodID invoke = env->GetMethodID(methodClass, "invoke", "(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;");
    jclass stringClass = env->FindClass("java/lang/String");

    if (!fileCtor || !toUri || !toUrl || !urlClCtor || !loadClass || !currentThread || !getContextCl
            || !getDeclaredMethod || !setAccessible || !invoke || !stringClass) {
        env->ExceptionClear();
        Log("method lookup failed");
        return false;
    }

    jstring jarPath = env->NewStringUTF(agentJar.c_str());
    jobject file = env->NewObject(fileClass, fileCtor, jarPath);
    jobject uri = env->CallObjectMethod(file, toUri);
    jobject url = env->CallObjectMethod(uri, toUrl);
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        Log("url resolve failed");
        return false;
    }

    jobjectArray urls = env->NewObjectArray(1, urlClass, url);
    jobject thread = env->CallStaticObjectMethod(threadClass, currentThread);
    jobject parent = env->CallObjectMethod(thread, getContextCl);
    jobject loader = env->NewObject(urlClClass, urlClCtor, urls, parent);
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        Log("classloader create failed");
        return false;
    }

    jstring className = env->NewStringUTF(agentClassName.c_str());
    jobject agentClass = env->CallObjectMethod(loader, loadClass, className);
    if (env->ExceptionCheck() || !agentClass) {
        env->ExceptionClear();
        Log("GhostAgent load failed");
        return false;
    }

    jobjectArray paramTypes = env->NewObjectArray(1, classClass, stringClass);
    jstring methodName = env->NewStringUTF("inject");
    jobject method = env->CallObjectMethod(agentClass, getDeclaredMethod, methodName, paramTypes);
    if (env->ExceptionCheck() || !method) {
        env->ExceptionClear();
        Log("inject method missing");
        return false;
    }
    env->CallVoidMethod(method, setAccessible, JNI_TRUE);
    jobjectArray args = env->NewObjectArray(1, env->FindClass("java/lang/Object"), env->NewStringUTF(payloadPath.c_str()));
    env->CallObjectMethod(method, invoke, nullptr, args);
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        Log("GhostAgent.inject failed");
        return false;
    }
    Log("GhostAgent.inject invoked from DLL");
    return true;
}

DWORD WINAPI InjectWorker(LPVOID) {
    Log("inject worker start");
    std::string agentJar;
    std::string payloadJar;
    std::string agentClass;
    std::string payloadVanilla;
    std::string forceLoader;
    if (!ReadConfig(agentJar, payloadJar, agentClass, payloadVanilla, forceLoader)) {
        Log("config missing");
        return 1;
    }

    HMODULE jvm = WaitForJvm(90000);
    if (!jvm) {
        Log("jvm.dll not found");
        return 2;
    }

    auto getCreated = reinterpret_cast<GetCreatedJavaVMs>(GetProcAddress(jvm, "JNI_GetCreatedJavaVMs"));
    if (!getCreated) {
        Log("JNI_GetCreatedJavaVMs missing");
        return 3;
    }

    JavaVM* vm = nullptr;
    jsize count = 0;
    for (int i = 0; i < 400; i++) {
        if (getCreated(&vm, 1, &count) == JNI_OK && count > 0 && vm) {
            break;
        }
        Sleep(50);
    }
    if (!vm || count <= 0) {
        Log("JavaVM not available");
        return 4;
    }

    JNIEnv* env = nullptr;
    jint attach = vm->AttachCurrentThread(reinterpret_cast<void**>(&env), nullptr);
    if (attach != JNI_OK || !env) {
        Log("AttachCurrentThread failed");
        return 5;
    }

    const std::string fabric = payloadJar.empty() ? agentJar : payloadJar;
    const std::string vanilla = payloadVanilla.empty() ? fabric : payloadVanilla;
    SetLoaderProps(env, fabric, vanilla, forceLoader);
    std::string agentArg = fabric + "|" + vanilla;
    if (!forceLoader.empty()) {
        agentArg += "|force=" + forceLoader;
    }
    bool ok = InvokeGhostAgent(env, agentJar, agentArg, agentClass);
    vm->DetachCurrentThread();
    Log(ok ? "inject worker done ok" : "inject worker done partial");
    return ok ? 0 : 6;
}

} // namespace

extern "C" __declspec(dllexport) DWORD WINAPI GhostInjectMain(LPVOID param) {
    return InjectWorker(param);
}

BOOL APIENTRY DllMain(HMODULE module, DWORD reason, LPVOID) {
    if (reason == DLL_PROCESS_ATTACH) {
        DisableThreadLibraryCalls(module);
    }
    return TRUE;
}
