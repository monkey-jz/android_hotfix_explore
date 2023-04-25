#include <jni.h>
#include <string>
extern "C" JNIEXPORT jstring
Java_com_jz_hotfixexplore_HotfixUtils_stringFromJNI(JNIEnv* env,jobject /* this */) {
    std::string hello = "Hello from C++";
    int arr[2];
    arr[0] = 2;
    arr[1] = 3;
    arr[2] = 4;
    return env->NewStringUTF(hello.c_str());
}