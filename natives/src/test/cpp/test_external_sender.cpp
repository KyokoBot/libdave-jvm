#include "external_sender.h"
#include "jni_utils.h"

#include <exception>

using namespace kyoko::libdave;
using discord::dave::test::ExternalSender;

namespace {
ExternalSender *fromHandle(jlong handle) {
  return reinterpret_cast<ExternalSender *>(handle);
}

void throwIllegalState(JNIEnv *env, const char *message) {
  LocalRefHolder<1> holder(env);
  jclass exc = holder.track(env->FindClass("java/lang/IllegalStateException"));
  if (exc != nullptr) {
    env->ThrowNew(exc, message);
  }
}
} // namespace

extern "C" {

JNIEXPORT jlong JNICALL Java_moe_kyokobot_libdave_TestExternalSender_create(
    JNIEnv *env, jclass clazz, jint protocolVersion, jlong groupId) {
  try {
    return reinterpret_cast<jlong>(
        new ExternalSender(static_cast<discord::dave::ProtocolVersion>(protocolVersion),
                           static_cast<uint64_t>(groupId)));
  } catch (const std::exception &e) {
    throwIllegalState(env, e.what());
    return 0;
  }
}

JNIEXPORT void JNICALL Java_moe_kyokobot_libdave_TestExternalSender_destroy(
    JNIEnv *env, jclass clazz, jlong handle) {
  delete fromHandle(handle);
}

JNIEXPORT jbyteArray JNICALL
Java_moe_kyokobot_libdave_TestExternalSender_getMarshalledExternalSender(
    JNIEnv *env, jclass clazz, jlong handle) {
  try {
    return toByteArray(env, fromHandle(handle)->GetMarshalledExternalSender());
  } catch (const std::exception &e) {
    throwIllegalState(env, e.what());
    return nullptr;
  }
}

JNIEXPORT jbyteArray JNICALL
Java_moe_kyokobot_libdave_TestExternalSender_proposeAdd(
    JNIEnv *env, jclass clazz, jlong handle, jint epoch, jbyteArray keyPackage) {
  std::vector<uint8_t> keyPackageVec;
  copyByteArrayToVector(env, keyPackage, keyPackageVec);
  try {
    return toByteArray(env, fromHandle(handle)->ProposeAdd(
                                static_cast<uint32_t>(epoch), keyPackageVec));
  } catch (const std::exception &e) {
    throwIllegalState(env, e.what());
    return nullptr;
  }
}

JNIEXPORT jobjectArray JNICALL
Java_moe_kyokobot_libdave_TestExternalSender_splitCommitWelcome(
    JNIEnv *env, jclass clazz, jlong handle, jbyteArray commitWelcome) {
  std::vector<uint8_t> commitWelcomeVec;
  copyByteArrayToVector(env, commitWelcome, commitWelcomeVec);
  try {
    auto [commit, welcome] =
        fromHandle(handle)->SplitCommitWelcome(commitWelcomeVec);

    LocalRefHolder<3> holder(env);
    jclass byteArrayClass = holder.track(env->FindClass("[B"));
    if (byteArrayClass == nullptr) {
      return nullptr;
    }
    jobjectArray result = env->NewObjectArray(2, byteArrayClass, nullptr);
    jbyteArray commitArray = holder.track(toByteArray(env, commit));
    jbyteArray welcomeArray = holder.track(toByteArray(env, welcome));
    if (result == nullptr || commitArray == nullptr || welcomeArray == nullptr) {
      return nullptr; // pending exception
    }
    env->SetObjectArrayElement(result, 0, commitArray);
    env->SetObjectArrayElement(result, 1, welcomeArray);
    return result;
  } catch (const std::exception &e) {
    throwIllegalState(env, e.what());
    return nullptr;
  }
}

} // extern "C"
