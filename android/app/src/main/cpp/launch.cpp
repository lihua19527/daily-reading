#include <jni.h>
#include <node.h>

#include <string>
#include <vector>

extern "C" JNIEXPORT jint JNICALL
Java_com_dailyreading_app_ReaderRuntime_startNode(JNIEnv* env, jclass, jobjectArray arguments) {
    std::vector<std::string> values{"node"};
    for (jsize i = 0; i < env->GetArrayLength(arguments); ++i) {
        auto value = static_cast<jstring>(env->GetObjectArrayElement(arguments, i));
        const char* bytes = env->GetStringUTFChars(value, nullptr);
        if (!bytes) return 1;
        values.emplace_back(bytes);
        env->ReleaseStringUTFChars(value, bytes);
        env->DeleteLocalRef(value);
    }

    std::vector<char*> args;
    for (auto& value : values) args.push_back(value.data());
    args.push_back(nullptr);
    return node::Start(static_cast<int>(values.size()), args.data());
}
