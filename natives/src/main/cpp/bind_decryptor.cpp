#include "jni_utils.h"
#include "kyoko_dave_internal.h"
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
int32_t mapDecryptorResult(IDecryptor::ResultCode result, size_t bytesWritten) {
  if (result == IDecryptor::Success) {
    return static_cast<int32_t>(bytesWritten);
  }
  return -static_cast<int32_t>(result);
}

IDecryptor *toDecryptor(kyoko_dave_decryptor *handle) {
  return reinterpret_cast<IDecryptor *>(handle);
}

kyoko_dave_decryptor *fromHandle(jlong handle) {
  return reinterpret_cast<kyoko_dave_decryptor *>(handle);
}
} // namespace

kyoko_dave_decryptor *kyoko_dave_decryptor_create(void) {
  return reinterpret_cast<kyoko_dave_decryptor *>(CreateDecryptor().release());
}

void kyoko_dave_decryptor_destroy(kyoko_dave_decryptor *decryptor) {
  delete toDecryptor(decryptor);
}

void kyoko_dave_decryptor_transition_to_key_ratchet(
    kyoko_dave_decryptor *decryptor, kyoko_dave_key_ratchet *key_ratchet) {
  toDecryptor(decryptor)->TransitionToKeyRatchet(
      std::unique_ptr<IKeyRatchet>(reinterpret_cast<IKeyRatchet *>(key_ratchet)));
}

void kyoko_dave_decryptor_transition_to_passthrough_mode(
    kyoko_dave_decryptor *decryptor, bool passthrough_mode) {
  toDecryptor(decryptor)->TransitionToPassthroughMode(passthrough_mode);
}

size_t kyoko_dave_decryptor_get_max_plaintext_byte_size(
    kyoko_dave_decryptor *decryptor, int32_t media_type,
    size_t encrypted_frame_size) {
  return toDecryptor(decryptor)->GetMaxPlaintextByteSize(
      static_cast<MediaType>(media_type), encrypted_frame_size);
}

int32_t kyoko_dave_decryptor_decrypt(kyoko_dave_decryptor *decryptor,
                                     int32_t media_type,
                                     const uint8_t *encrypted_frame,
                                     size_t encrypted_frame_size,
                                     uint8_t *frame, size_t frame_capacity) {
  size_t bytesWritten = 0;
  auto result = toDecryptor(decryptor)->Decrypt(
      static_cast<MediaType>(media_type),
      MakeArrayView(encrypted_frame, encrypted_frame_size),
      MakeArrayView(frame, frame_capacity), &bytesWritten);

  return mapDecryptorResult(result, bytesWritten);
}

JNIEXPORT jlong JNICALL
Java_moe_kyokobot_libdave_natives_DaveNativeBindings_daveDecryptorCreate(
    JNIEnv *env, jobject clazz) {
  return reinterpret_cast<jlong>(kyoko_dave_decryptor_create());
}

JNIEXPORT void JNICALL
Java_moe_kyokobot_libdave_natives_DaveNativeBindings_daveDecryptorDestroy(
    JNIEnv *env, jobject clazz, jlong handle) {
  kyoko_dave_decryptor_destroy(fromHandle(handle));
}

JNIEXPORT void JNICALL
Java_moe_kyokobot_libdave_natives_DaveNativeBindings_daveDecryptorTransitionToKeyRatchet(
    JNIEnv *env, jobject clazz, jlong handle, jlong keyRatchetHandle) {
  kyoko_dave_decryptor_transition_to_key_ratchet(
      fromHandle(handle),
      reinterpret_cast<kyoko_dave_key_ratchet *>(keyRatchetHandle));
}

JNIEXPORT void JNICALL
Java_moe_kyokobot_libdave_natives_DaveNativeBindings_daveDecryptorTransitionToPassthroughMode(
    JNIEnv *env, jobject clazz, jlong handle, jboolean passthroughMode) {
  kyoko_dave_decryptor_transition_to_passthrough_mode(fromHandle(handle),
                                                      passthroughMode);
}

JNIEXPORT jlong JNICALL
Java_moe_kyokobot_libdave_natives_DaveNativeBindings_daveDecryptorGetMaxPlaintextByteSize(
    JNIEnv *env, jobject clazz, jlong handle, jint mediaType,
    jlong encryptedFrameSize) {
  return static_cast<jlong>(kyoko_dave_decryptor_get_max_plaintext_byte_size(
      fromHandle(handle), mediaType, static_cast<size_t>(encryptedFrameSize)));
}

JNIEXPORT jint JNICALL
Java_moe_kyokobot_libdave_natives_DaveNativeBindings_daveDecryptorDecrypt__JI_3B_3B(
    JNIEnv *env, jobject clazz, jlong handle, jint mediaType,
    jbyteArray encryptedFrame, jbyteArray frame) {
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

  auto result = kyoko_dave_decryptor_decrypt(
      fromHandle(handle), mediaType,
      reinterpret_cast<const uint8_t *>(encryptedFrameBytes),
      static_cast<size_t>(encryptedFrameLen),
      reinterpret_cast<uint8_t *>(frameBytes), static_cast<size_t>(frameLen));

  env->ReleaseByteArrayElements(encryptedFrame, encryptedFrameBytes, JNI_ABORT);
  env->ReleaseByteArrayElements(frame, frameBytes, 0);

  return result;
}

JNIEXPORT jint JNICALL
Java_moe_kyokobot_libdave_natives_DaveNativeBindings_daveDecryptorDecrypt__JILjava_nio_ByteBuffer_2Ljava_nio_ByteBuffer_2(
    JNIEnv *env, jobject clazz, jlong handle, jint mediaType,
    jobject encryptedFrame, jobject frame) {
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

  return kyoko_dave_decryptor_decrypt(
      fromHandle(handle), mediaType, encryptedFrameInfo.address,
      encryptedFrameInfo.length, frameInfo.address, frameInfo.length);
}

JNIEXPORT jint JNICALL
Java_moe_kyokobot_libdave_natives_DaveNativeBindings_daveDecryptorDecrypt__JIJIJI(
    JNIEnv *env, jobject clazz, jlong handle, jint mediaType,
    jlong encryptedFramePtr, jint encryptedFrameSize, jlong framePtr,
    jint frameCapacity) {
  if (encryptedFramePtr == 0 || framePtr == 0 || encryptedFrameSize < 0 ||
      frameCapacity < 0) {
    throwIllegalArgument(env, "Invalid frame pointer or size");
    return -IDecryptor::DecryptionFailure;
  }

  return kyoko_dave_decryptor_decrypt(
      fromHandle(handle), mediaType,
      reinterpret_cast<const uint8_t *>(encryptedFramePtr),
      static_cast<size_t>(encryptedFrameSize),
      reinterpret_cast<uint8_t *>(framePtr),
      static_cast<size_t>(frameCapacity));
}
