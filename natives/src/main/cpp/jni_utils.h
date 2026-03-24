#ifndef JNI_UTILS
#define JNI_UTILS

#include <array>
#include <cstdint>
#include <functional>
#include <jni.h>
#include <memory>
#include <string>
#include <vector>

#if defined(__ANDROID__)
#define JNI_ATTACH_ENV_TYPE JNIEnv**
#define JNI_KEY_PAIR_CONTEXT(env, contextStr) (env)
#else
#define JNI_ATTACH_ENV_TYPE void**
#define JNI_KEY_PAIR_CONTEXT(env, contextStr) (contextStr)
#endif

namespace kyoko::libdave {

template <size_t N> class LocalRefHolder {
private:
  JNIEnv *env_;
  std::array<jobject, N> refs_;
  size_t count_;

public:
  explicit LocalRefHolder(JNIEnv *env) noexcept
      : env_(env), refs_{}, count_(0) {}

  ~LocalRefHolder() {
    for (size_t i = 0; i < count_; ++i) {
      if (refs_[i] != nullptr) {
        env_->DeleteLocalRef(refs_[i]);
      }
    }
  }

  LocalRefHolder(const LocalRefHolder &) = delete;
  LocalRefHolder &operator=(const LocalRefHolder &) = delete;
  LocalRefHolder(LocalRefHolder &&) = delete;
  LocalRefHolder &operator=(LocalRefHolder &&) = delete;

  template <typename T> T track(T ref) noexcept {
    if (count_ < N) {
      refs_[count_++] = static_cast<jobject>(ref);
    }
    return ref;
  }

  bool canTrack() const noexcept { return count_ < N; }

  size_t size() const noexcept { return count_; }
};

static inline ::jstring toJString(JNIEnv *env, const std::string &str) {
  return env->NewStringUTF(str.c_str());
}

static inline bool copyByteArrayToVector(JNIEnv *env, jbyteArray array,
                           std::vector<uint8_t> &vector) {
  jsize length = env->GetArrayLength(array);
  if (length < 0) {
    return false;
  }
  vector.resize(length);
  env->GetByteArrayRegion(array, 0, length,
                          reinterpret_cast<jbyte *>(vector.data()));
  return true;
}

static inline ::jbyteArray toByteArray(JNIEnv *env, const std::vector<uint8_t> &vector) {
  auto arraySize = static_cast<jsize>(vector.size());
  auto array = env->NewByteArray(arraySize);
  env->SetByteArrayRegion(array, 0, arraySize,
                          reinterpret_cast<const jbyte *>(vector.data()));
  return array;
}

static inline void throwIllegalArgument(JNIEnv *env, const char *message) {
  LocalRefHolder<1> holder(env);
  jclass exc =
      holder.track(env->FindClass("java/lang/IllegalArgumentException"));
  if (exc != nullptr) {
    env->ThrowNew(exc, message);
  }
}

static inline ::jobject boxedInteger(JNIEnv *env, int value) {
  LocalRefHolder<1> refs(env);

  jclass integerClass = refs.track(env->FindClass("java/lang/Integer"));
  if (integerClass == nullptr) {
    return nullptr;
  }

  jmethodID valueOfMethod =
      env->GetStaticMethodID(integerClass, "valueOf", "(I)Ljava/lang/Integer;");
  if (valueOfMethod == nullptr) {
    return nullptr;
  }

  return env->CallStaticObjectMethod(integerClass, valueOfMethod, (jint)value);
}

struct DirectBufferInfo {
  uint8_t *address;
  size_t length;
};

static inline bool getDirectBufferInfo(JNIEnv *env, jobject buffer, DirectBufferInfo &info) {
  void *addr = env->GetDirectBufferAddress(buffer);
  if (addr == nullptr) {
    return false;
  }

  // Get java.nio.Buffer class to access position/limit
  // We can assume buffer is an instance of it.
  // Using FindClass("java/nio/Buffer") is safer than GetObjectClass because
  // the object might be a specific subclass (DirectByteBuffer) but methods are
  // on Buffer.
  LocalRefHolder<1> holder(env);
  jclass bufferClass = holder.track(env->FindClass("java/nio/Buffer"));
  if (bufferClass == nullptr) {
    return false;
  }

  jmethodID positionId = env->GetMethodID(bufferClass, "position", "()I");
  jmethodID limitId = env->GetMethodID(bufferClass, "limit", "()I");

  if (positionId == nullptr || limitId == nullptr) {
    return false;
  }

  jint position = env->CallIntMethod(buffer, positionId);
  jint limit = env->CallIntMethod(buffer, limitId);

  if (position < 0 || limit < position) {
    return false;
  }

  info.address = static_cast<uint8_t *>(addr) + position;
  info.length = static_cast<size_t>(limit - position);

  return true;
}

class JNICallbackWrapper {
private:
  JavaVM *jvm;
  jobject callback;
  jmethodID methodId;

  void callMethod(JNIEnv *env, const std::string &arg1,
                  const std::string &arg2) {
    LocalRefHolder<2> holder(env);
    jstring jarg1 = holder.track(toJString(env, arg1));
    jstring jarg2 = holder.track(toJString(env, arg2));

    env->CallVoidMethod(callback, methodId, jarg1, jarg2);
  }

  void callMethod(JNIEnv *env, jbyteArray arg1) {
    LocalRefHolder<1> holder(env);
    holder.track(arg1);
    env->CallVoidMethod(callback, methodId, arg1);
  }

  void callMethod(JNIEnv *env) {
    env->CallVoidMethod(callback, methodId);
  }

  void callMethod(JNIEnv *env, jint arg1, const std::string &arg2, jint arg3,
                  const std::string &arg4) {
    LocalRefHolder<2> holder(env);
    jstring jarg2 = holder.track(toJString(env, arg2));
    jstring jarg4 = holder.track(toJString(env, arg4));

    env->CallVoidMethod(callback, methodId, arg1, jarg2, arg3, jarg4);
  }

public:
  JNICallbackWrapper(JNIEnv *env, jobject callback, const char *methodName,
                     const char *signature)
      : jvm(nullptr), callback(nullptr), methodId(nullptr) {

    if (callback == nullptr) {
      return;
    }

    // Get JavaVM for thread attachment
    if (env->GetJavaVM(&jvm) != JNI_OK) {
      return;
    }

    // Create global reference (survives across threads and JNI calls)
    this->callback = env->NewGlobalRef(callback);
    if (this->callback == nullptr) {
      return;
    }

    // Get method ID
    LocalRefHolder<1> holder(env);
    jclass callbackClass = holder.track(env->GetObjectClass(this->callback));
    methodId = env->GetMethodID(callbackClass, methodName, signature);

    if (methodId == nullptr) {
      env->DeleteGlobalRef(this->callback);
      this->callback = nullptr;
    }
  }

  ~JNICallbackWrapper() {
    if (callback != nullptr && jvm != nullptr) {
      JNIEnv *env = getEnv();
      if (env != nullptr) {
        env->DeleteGlobalRef(callback);
      }
    }
  }

  JNICallbackWrapper(const JNICallbackWrapper &) = delete;
  JNICallbackWrapper &operator=(const JNICallbackWrapper &) = delete;
  JNICallbackWrapper(JNICallbackWrapper &&other) noexcept
      : jvm(other.jvm), callback(other.callback), methodId(other.methodId) {
    other.jvm = nullptr;
    other.callback = nullptr;
    other.methodId = nullptr;
  }

  bool isValid() const { return callback != nullptr && methodId != nullptr; }

  JNIEnv *getEnv() const {
    if (jvm == nullptr) {
      return nullptr;
    }

    JNIEnv *env = nullptr;
    jint result = jvm->GetEnv((void **)&env, JNI_VERSION_1_6);

    if (result == JNI_EDETACHED) {
      result = jvm->AttachCurrentThread((JNI_ATTACH_ENV_TYPE)&env, nullptr);
      if (result != JNI_OK) {
        return nullptr;
      }
    }

    return env;
  }

  template <typename... Args> void invoke(Args... args) {
    if (!isValid()) {
      return;
    }

    JNIEnv *env = getEnv();
    if (env == nullptr) {
      return;
    }

    callMethod(env, args...);

    if (env->ExceptionCheck()) {
      env->ExceptionDescribe();
      env->ExceptionClear();
    }
  }
};

} // namespace kyoko::libdave

#endif // JNI_UTILS
