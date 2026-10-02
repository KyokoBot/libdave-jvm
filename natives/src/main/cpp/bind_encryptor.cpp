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
// EncryptionFailure = 1
// We return bytesWritten (positive) on success.
// On failure, we return -ResultCode (negative).
jint mapEncryptorResult(IEncryptor::ResultCode result, size_t bytesWritten) {
  if (result == IEncryptor::Success) {
    return static_cast<jint>(bytesWritten);
  }
  // Return negative result code
  return -static_cast<jint>(result);
}
} // namespace

JNIEXPORT jlong JNICALL
Java_moe_kyokobot_libdave_natives_DaveNativeBindings_daveEncryptorCreate(
    JNIEnv *env, jobject clazz) {
  auto encryptor = CreateEncryptor();
  return reinterpret_cast<jlong>(encryptor.release());
}

JNIEXPORT void JNICALL
Java_moe_kyokobot_libdave_natives_DaveNativeBindings_daveEncryptorDestroy(
    JNIEnv *env, jobject clazz, jlong handle) {
  auto encryptor = reinterpret_cast<IEncryptor *>(handle);
  delete encryptor;
}

JNIEXPORT void JNICALL
Java_moe_kyokobot_libdave_natives_DaveNativeBindings_daveEncryptorSetKeyRatchet(
    JNIEnv *env, jobject clazz, jlong handle, jlong keyRatchetHandle) {
  auto encryptor = reinterpret_cast<IEncryptor *>(handle);
  auto keyRatchet = reinterpret_cast<IKeyRatchet *>(keyRatchetHandle);
  encryptor->SetKeyRatchet(std::unique_ptr<IKeyRatchet>(keyRatchet));
}

JNIEXPORT void JNICALL
Java_moe_kyokobot_libdave_natives_DaveNativeBindings_daveEncryptorSetPassthroughMode(
    JNIEnv *env, jobject clazz, jlong handle, jboolean passthroughMode) {
  auto encryptor = reinterpret_cast<IEncryptor *>(handle);
  encryptor->SetPassthroughMode(passthroughMode);
}

JNIEXPORT void JNICALL
Java_moe_kyokobot_libdave_natives_DaveNativeBindings_daveEncryptorAssignSsrcToCodec(
    JNIEnv *env, jobject clazz, jlong handle, jint ssrc, jint codec) {
  auto encryptor = reinterpret_cast<IEncryptor *>(handle);
  encryptor->AssignSsrcToCodec(static_cast<uint32_t>(ssrc),
                               static_cast<Codec>(codec));
}

JNIEXPORT jint JNICALL
Java_moe_kyokobot_libdave_natives_DaveNativeBindings_daveEncryptorGetProtocolVersion(
    JNIEnv *env, jobject clazz, jlong handle) {
  auto encryptor = reinterpret_cast<IEncryptor *>(handle);
  return static_cast<jint>(encryptor->GetProtocolVersion());
}

JNIEXPORT jlong JNICALL
Java_moe_kyokobot_libdave_natives_DaveNativeBindings_daveEncryptorGetMaxCiphertextByteSize(
    JNIEnv *env, jobject clazz, jlong handle, jint mediaType, jlong frameSize) {
  auto encryptor = reinterpret_cast<IEncryptor *>(handle);
  return static_cast<jlong>(encryptor->GetMaxCiphertextByteSize(
      static_cast<MediaType>(mediaType), static_cast<size_t>(frameSize)));
}

JNIEXPORT jint JNICALL
Java_moe_kyokobot_libdave_natives_DaveNativeBindings_daveEncryptorEncrypt__JII_3B_3B(
    JNIEnv *env, jobject clazz, jlong handle, jint mediaType, jint ssrc,
    jbyteArray frame, jbyteArray encryptedFrame) {
  auto encryptor = reinterpret_cast<IEncryptor *>(handle);

  jboolean isCopy;
  jbyte *frameBytes = env->GetByteArrayElements(frame, &isCopy);
  if (frameBytes == nullptr) {
    return -1; // pending exception
  }
  jsize frameLen = env->GetArrayLength(frame);

  jbyte *encryptedFrameBytes =
      env->GetByteArrayElements(encryptedFrame, &isCopy);
  if (encryptedFrameBytes == nullptr) {
    env->ReleaseByteArrayElements(frame, frameBytes, JNI_ABORT);
    return -1; // pending exception
  }
  jsize encryptedFrameLen = env->GetArrayLength(encryptedFrame);

  size_t bytesWritten = 0;
  auto result = encryptor->Encrypt(
      static_cast<MediaType>(mediaType), static_cast<uint32_t>(ssrc),
      MakeArrayView(reinterpret_cast<const uint8_t *>(frameBytes), frameLen),
      MakeArrayView(reinterpret_cast<uint8_t *>(encryptedFrameBytes),
                    encryptedFrameLen),
      &bytesWritten);

  env->ReleaseByteArrayElements(frame, frameBytes, JNI_ABORT);
  // Commit changes to encryptedFrame
  env->ReleaseByteArrayElements(encryptedFrame, encryptedFrameBytes, 0);

  return mapEncryptorResult(result, bytesWritten);
}

JNIEXPORT jint JNICALL
Java_moe_kyokobot_libdave_natives_DaveNativeBindings_daveEncryptorEncrypt__JIILjava_nio_ByteBuffer_2Ljava_nio_ByteBuffer_2(
    JNIEnv *env, jobject clazz, jlong handle, jint mediaType, jint ssrc,
    jobject frame, jobject encryptedFrame) {
  auto encryptor = reinterpret_cast<IEncryptor *>(handle);

  DirectBufferInfo frameInfo;
  if (!getDirectBufferInfo(env, frame, frameInfo)) {
    throwIllegalArgument(env, "frame must be a direct ByteBuffer");
    return -1;
  }

  DirectBufferInfo encryptedFrameInfo;
  if (!getDirectBufferInfo(env, encryptedFrame, encryptedFrameInfo)) {
    throwIllegalArgument(env, "encryptedFrame must be a direct ByteBuffer");
    return -1;
  }

  size_t bytesWritten = 0;
  auto result = encryptor->Encrypt(
      static_cast<MediaType>(mediaType), static_cast<uint32_t>(ssrc),
      MakeArrayView(reinterpret_cast<const uint8_t *>(frameInfo.address),
                    frameInfo.length),
      MakeArrayView(reinterpret_cast<uint8_t *>(encryptedFrameInfo.address),
                    encryptedFrameInfo.length),
      &bytesWritten);

  return mapEncryptorResult(result, bytesWritten);
}

JNIEXPORT jint JNICALL
Java_moe_kyokobot_libdave_natives_DaveNativeBindings_daveEncryptorEncrypt__JIIJIJI(
    JNIEnv *env, jobject clazz, jlong handle, jint mediaType, jint ssrc,
    jlong framePtr, jint frameSize, jlong encryptedFramePtr,
    jint encryptedFrameCapacity) {
  auto encryptor = reinterpret_cast<IEncryptor *>(handle);

  if (framePtr == 0 || encryptedFramePtr == 0 || frameSize < 0 ||
      encryptedFrameCapacity < 0) {
    throwIllegalArgument(env, "Invalid frame pointer or size");
    return -1;
  }

  auto frameBytes = reinterpret_cast<const uint8_t *>(framePtr);
  auto encryptedFrameBytes = reinterpret_cast<uint8_t *>(encryptedFramePtr);

  size_t bytesWritten = 0;
  auto result = encryptor->Encrypt(
      static_cast<MediaType>(mediaType), static_cast<uint32_t>(ssrc),
      MakeArrayView(frameBytes, static_cast<size_t>(frameSize)),
      MakeArrayView(encryptedFrameBytes,
                    static_cast<size_t>(encryptedFrameCapacity)),
      &bytesWritten);

  return mapEncryptorResult(result, bytesWritten);
}

JNIEXPORT void JNICALL
Java_moe_kyokobot_libdave_natives_DaveNativeBindings_daveEncryptorSetProtocolVersionChangedCallback(
    JNIEnv *env, jobject clazz, jlong handle, jobject callback) {
  auto encryptor = reinterpret_cast<IEncryptor *>(handle);

  auto callbackWrapper = std::make_shared<JNICallbackWrapper>(
      env, callback, "onProtocolVersionChanged", "()V");

  encryptor->SetProtocolVersionChangedCallback([callbackWrapper]() {
    if (callbackWrapper && callbackWrapper->isValid()) {
      auto env = callbackWrapper->getEnv();
      if (env) {
        callbackWrapper->invoke();
      }
    }
  });
}
