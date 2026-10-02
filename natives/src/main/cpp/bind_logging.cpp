#include "jni_utils.h"
#include "moe_kyokobot_libdave_natives_DaveNativeBindings.h"
#include <dave/logger.h>

#include <mutex>

using namespace kyoko::libdave;
using namespace discord::dave;

namespace {

std::shared_ptr<JNICallbackWrapper> gLogSinkWrapper;
std::mutex gLogSinkMutex;

void NullLogSink(LoggingSeverity severity, const char *file, int line,
                 const std::string &message) {
  (void)severity;
  (void)file;
  (void)line;
  (void)message;
}

void JvmLogSink(LoggingSeverity severity, const char *file, int line,
                const std::string &message) {
  std::shared_ptr<JNICallbackWrapper> wrapper;
  {
    std::lock_guard<std::mutex> lock(gLogSinkMutex);
    wrapper = gLogSinkWrapper;
  }

  if (wrapper && wrapper->isValid()) {
    wrapper->invoke((jint)severity, file != nullptr ? std::string(file) : std::string(),
                    (jint)line, message);
  }
}

} // namespace

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
  (void)vm;
  (void)reserved;
  SetLogSink(NullLogSink);
  return JNI_VERSION_1_6;
}

JNIEXPORT void JNICALL JNI_OnUnload(JavaVM *vm, void *reserved) {
  (void)vm;
  (void)reserved;
  SetLogSink(NullLogSink);
  {
    std::lock_guard<std::mutex> lock(gLogSinkMutex);
    gLogSinkWrapper.reset();
  }
}

JNIEXPORT void JNICALL
Java_moe_kyokobot_libdave_natives_DaveNativeBindings_daveSetLogSink(
    JNIEnv *env, jobject clazz, jobject sink) {
  (void)clazz;

  std::lock_guard<std::mutex> lock(gLogSinkMutex);

  if (sink != nullptr) {
    gLogSinkWrapper = std::make_shared<JNICallbackWrapper>(
        env, sink, "log", "(ILjava/lang/String;ILjava/lang/String;)V");

    if (gLogSinkWrapper->isValid()) {
      SetLogSink(JvmLogSink);
    } else {
      gLogSinkWrapper.reset();
      SetLogSink(NullLogSink);
    }
  } else {
    gLogSinkWrapper.reset();
    SetLogSink(NullLogSink);
  }
}
