#include "jni_utils.h"
#include "kyoko_dave_internal.h"
#include "moe_kyokobot_libdave_natives_DaveNativeBindings.h"
#include <dave/logger.h>

#include <mutex>

using namespace kyoko::libdave;
using namespace discord::dave;

namespace {

using LogSinkHolder = CallbackHolder<kyoko_dave_log_sink_callback>;

// Leaked intentionally: a static destructor would release the sink's user_data
// which would cause the JVM to abort.
std::shared_ptr<LogSinkHolder> &gLogSink =
    *new std::shared_ptr<LogSinkHolder>();
std::mutex gLogSinkMutex;

void NullLogSink(LoggingSeverity severity, const char *file, int line,
                 const std::string &message) {
  (void)severity;
  (void)file;
  (void)line;
  (void)message;
}

void ForwardingLogSink(LoggingSeverity severity, const char *file, int line,
                       const std::string &message) {
  std::shared_ptr<LogSinkHolder> sink;
  {
    std::lock_guard<std::mutex> lock(gLogSinkMutex);
    sink = gLogSink;
  }

  if (sink) {
    (*sink)(static_cast<int32_t>(severity), file, static_cast<int32_t>(line),
            message.c_str());
  }
}

void JvmLogSink(int32_t severity, const char *file, int32_t line,
                const char *message, void *userData) {
  static_cast<JNICallbackWrapper *>(userData)->invoke(
      static_cast<jint>(severity),
      file != nullptr ? std::string(file) : std::string(),
      static_cast<jint>(line), std::string(message));
}

} // namespace

void kyoko_dave_set_log_sink(kyoko_dave_log_sink_callback callback,
                             void *user_data,
                             kyoko_dave_user_data_free user_data_free) {
  auto sink = makeCallbackHolder(callback, user_data, user_data_free);
  if (!*sink) {
    sink.reset();
  }

  // Swap the previous sink out so its user_data is released outside the lock.
  {
    std::lock_guard<std::mutex> lock(gLogSinkMutex);
    gLogSink.swap(sink);
    SetLogSink(gLogSink ? ForwardingLogSink : NullLogSink);
  }
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
  (void)vm;
  (void)reserved;
  kyoko_dave_set_log_sink(nullptr, nullptr, nullptr);
  return JNI_VERSION_1_6;
}

JNIEXPORT void JNICALL JNI_OnUnload(JavaVM *vm, void *reserved) {
  (void)vm;
  (void)reserved;
  kyoko_dave_set_log_sink(nullptr, nullptr, nullptr);
}

JNIEXPORT void JNICALL
Java_moe_kyokobot_libdave_natives_DaveNativeBindings_daveSetLogSink(
    JNIEnv *env, jobject clazz, jobject sink) {
  (void)clazz;

  auto wrapper = newCallbackWrapper(
      env, sink, "log", "(ILjava/lang/String;ILjava/lang/String;)V");
  if (wrapper == nullptr) {
    kyoko_dave_set_log_sink(nullptr, nullptr, nullptr);
    return;
  }

  kyoko_dave_set_log_sink(JvmLogSink, wrapper, deleteCallbackWrapper);
}
