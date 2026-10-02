#include "jni_utils.h"
#include "moe_kyokobot_libdave_natives_DaveNativeBindings.h"
#include <dave/array_view.h>
#include <dave/dave.h>
#include <dave/dave_interfaces.h>

using namespace kyoko::libdave;
using namespace discord::dave;

namespace {
// ResultCode mapping
// Success = 0
// DecryptionFailure = 1
// MissingKeyRatchet = 2
// InvalidNonce = 3
// MissingCryptor = 4
// We return bytesWritten (positive) on success.
// On failure, we return -ResultCode (negative).
jint mapDecryptorResult(IDecryptor::ResultCode result, size_t bytesWritten) {
  if (result == IDecryptor::Success) {
    return static_cast<jint>(bytesWritten);
  }
  return -static_cast<jint>(result);
}
} // namespace

JNIEXPORT jlong JNICALL
Java_moe_kyokobot_libdave_natives_DaveNativeBindings_daveDecryptorCreate(
    JNIEnv *env, jobject clazz) {
  auto decryptor = CreateDecryptor();
  return reinterpret_cast<jlong>(decryptor.release());
}

JNIEXPORT void JNICALL
Java_moe_kyokobot_libdave_natives_DaveNativeBindings_daveDecryptorDestroy(
    JNIEnv *env, jobject clazz, jlong handle) {
  auto decryptor = reinterpret_cast<IDecryptor *>(handle);
  delete decryptor;
}

JNIEXPORT void JNICALL
Java_moe_kyokobot_libdave_natives_DaveNativeBindings_daveDecryptorTransitionToKeyRatchet(
    JNIEnv *env, jobject clazz, jlong handle, jlong keyRatchetHandle) {
  auto decryptor = reinterpret_cast<IDecryptor *>(handle);
  auto keyRatchet = reinterpret_cast<IKeyRatchet *>(keyRatchetHandle);
  decryptor->TransitionToKeyRatchet(std::unique_ptr<IKeyRatchet>(keyRatchet));
}

JNIEXPORT void JNICALL
Java_moe_kyokobot_libdave_natives_DaveNativeBindings_daveDecryptorTransitionToPassthroughMode(
    JNIEnv *env, jobject clazz, jlong handle, jboolean passthroughMode) {
  auto decryptor = reinterpret_cast<IDecryptor *>(handle);
  decryptor->TransitionToPassthroughMode(passthroughMode);
}

JNIEXPORT jlong JNICALL
Java_moe_kyokobot_libdave_natives_DaveNativeBindings_daveDecryptorGetMaxPlaintextByteSize(
    JNIEnv *env, jobject clazz, jlong handle, jint mediaType,
    jlong encryptedFrameSize) {
  auto decryptor = reinterpret_cast<IDecryptor *>(handle);
  return static_cast<jlong>(decryptor->GetMaxPlaintextByteSize(
      static_cast<MediaType>(mediaType),
      static_cast<size_t>(encryptedFrameSize)));
}

JNIEXPORT jint JNICALL
Java_moe_kyokobot_libdave_natives_DaveNativeBindings_daveDecryptorDecrypt__JI_3B_3B(
    JNIEnv *env, jobject clazz, jlong handle, jint mediaType,
    jbyteArray encryptedFrame, jbyteArray frame) {
  auto decryptor = reinterpret_cast<IDecryptor *>(handle);

  jboolean isCopy;
  jbyte *encryptedFrameBytes =
      env->GetByteArrayElements(encryptedFrame, &isCopy);
  if (encryptedFrameBytes == nullptr) {
    return -IDecryptor::DecryptionFailure; // pending exception
  }
  jsize encryptedFrameLen = env->GetArrayLength(encryptedFrame);

  jbyte *frameBytes = env->GetByteArrayElements(frame, &isCopy);
  if (frameBytes == nullptr) {
    env->ReleaseByteArrayElements(encryptedFrame, encryptedFrameBytes,
                                  JNI_ABORT);
    return -IDecryptor::DecryptionFailure; // pending exception
  }
  jsize frameLen = env->GetArrayLength(frame);

  size_t bytesWritten = 0;
  auto result = decryptor->Decrypt(
      static_cast<MediaType>(mediaType),
      MakeArrayView(reinterpret_cast<const uint8_t *>(encryptedFrameBytes),
                    encryptedFrameLen),
      MakeArrayView(reinterpret_cast<uint8_t *>(frameBytes), frameLen),
      &bytesWritten);

  env->ReleaseByteArrayElements(encryptedFrame, encryptedFrameBytes, JNI_ABORT);
  env->ReleaseByteArrayElements(frame, frameBytes, 0);

  return mapDecryptorResult(result, bytesWritten);
}

JNIEXPORT jint JNICALL
Java_moe_kyokobot_libdave_natives_DaveNativeBindings_daveDecryptorDecrypt__JILjava_nio_ByteBuffer_2Ljava_nio_ByteBuffer_2(
    JNIEnv *env, jobject clazz, jlong handle, jint mediaType,
    jobject encryptedFrame, jobject frame) {
  auto decryptor = reinterpret_cast<IDecryptor *>(handle);

  DirectBufferInfo encryptedFrameInfo;
  if (!getDirectBufferInfo(env, encryptedFrame, encryptedFrameInfo)) {
    throwIllegalArgument(env, "encryptedFrame must be a direct ByteBuffer");
    return -IDecryptor::DecryptionFailure;
  }

  DirectBufferInfo frameInfo;
  if (!getDirectBufferInfo(env, frame, frameInfo)) {
    throwIllegalArgument(env, "frame must be a direct ByteBuffer");
    return -IDecryptor::DecryptionFailure;
  }

  size_t bytesWritten = 0;
  auto result = decryptor->Decrypt(
      static_cast<MediaType>(mediaType),
      MakeArrayView(
          reinterpret_cast<const uint8_t *>(encryptedFrameInfo.address),
          encryptedFrameInfo.length),
      MakeArrayView(reinterpret_cast<uint8_t *>(frameInfo.address),
                    frameInfo.length),
      &bytesWritten);

  return mapDecryptorResult(result, bytesWritten);
}

JNIEXPORT jint JNICALL
Java_moe_kyokobot_libdave_natives_DaveNativeBindings_daveDecryptorDecrypt__JIJIJI(
    JNIEnv *env, jobject clazz, jlong handle, jint mediaType,
    jlong encryptedFramePtr, jint encryptedFrameSize, jlong framePtr,
    jint frameCapacity) {
  auto decryptor = reinterpret_cast<IDecryptor *>(handle);

  if (encryptedFramePtr == 0 || framePtr == 0 || encryptedFrameSize < 0 ||
      frameCapacity < 0) {
    throwIllegalArgument(env, "Invalid frame pointer or size");
    return -IDecryptor::DecryptionFailure;
  }

  auto encryptedFrameBytes =
      reinterpret_cast<const uint8_t *>(encryptedFramePtr);
  auto frameBytes = reinterpret_cast<uint8_t *>(framePtr);

  size_t bytesWritten = 0;
  auto result = decryptor->Decrypt(
      static_cast<MediaType>(mediaType),
      MakeArrayView(encryptedFrameBytes,
                    static_cast<size_t>(encryptedFrameSize)),
      MakeArrayView(frameBytes, static_cast<size_t>(frameCapacity)),
      &bytesWritten);

  return mapDecryptorResult(result, bytesWritten);
}
