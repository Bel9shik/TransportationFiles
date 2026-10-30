// ABI-compatible replacement for irondash_engine_context 0.5.5's Android JNI
// shim, built with our NDK instead of its 4-KB-aligned precompiled binary.
// Adapted from https://github.com/irondash/irondash/tree/main/engine_context
// Copyright (c) 2022 Matej Knopp and contributors. MIT; see LICENSE.irondash.
#include <jni.h>
#include <android/looper.h>

static JavaVM* shared_vm = nullptr;
static ALooper* main_looper = nullptr;
static jobject class_loader = nullptr;

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void*) {
  JNIEnv* env = nullptr;
  if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) return JNI_ERR;
  jclass flutter = env->FindClass("io/flutter/embedding/engine/FlutterJNI");
  if (!flutter) return JNI_ERR;
  jclass meta = env->GetObjectClass(flutter);
  jmethodID method = env->GetMethodID(meta, "getClassLoader", "()Ljava/lang/ClassLoader;");
  if (!method) return JNI_ERR;
  jobject loader = env->CallObjectMethod(flutter, method);
  if (env->ExceptionCheck() || !loader) return JNI_ERR;
  class_loader = env->NewGlobalRef(loader);
  env->DeleteLocalRef(loader);
  env->DeleteLocalRef(meta);
  env->DeleteLocalRef(flutter);
  shared_vm = vm;
  main_looper = ALooper_forThread();
  return class_loader && main_looper ? JNI_VERSION_1_6 : JNI_ERR;
}
extern "C" JNIEXPORT JavaVM* irondash_engine_context_get_java_vm() { return shared_vm; }
extern "C" JNIEXPORT jobject irondash_engine_context_get_class_loader() { return class_loader; }
extern "C" JNIEXPORT void* irondash_engine_context_get_main_looper() { return main_looper; }
