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
// EncryptionFailure = 1
// We return bytesWritten (positive) on success.
// On failure, we return -ResultCode (negative).
int32_t mapEncryptorResult(IEncryptor::ResultCode result, size_t bytesWritten) {
  if (result == IEncryptor::Success) {
    return static_cast<int32_t>(bytesWritten);
  }
  // Return negative result code
  return -static_cast<int32_t>(result);
}

IEncryptor *toEncryptor(kyoko_dave_encryptor *handle) {
  return reinterpret_cast<IEncryptor *>(handle);
}

kyoko_dave_encryptor *fromHandle(jlong handle) {
  return reinterpret_cast<kyoko_dave_encryptor *>(handle);
}

void JvmProtocolVersionChangedCallback(void *userData) {
  static_cast<JNICallbackWrapper *>(userData)->invoke();
}
} // namespace

kyoko_dave_encryptor *kyoko_dave_encryptor_create(void) {
  return reinterpret_cast<kyoko_dave_encryptor *>(CreateEncryptor().release());
}

void kyoko_dave_encryptor_destroy(kyoko_dave_encryptor *encryptor) {
  delete toEncryptor(encryptor);
}

void kyoko_dave_encryptor_set_key_ratchet(kyoko_dave_encryptor *encryptor,
                                          kyoko_dave_key_ratchet *key_ratchet) {
  toEncryptor(encryptor)->SetKeyRatchet(
      std::unique_ptr<IKeyRatchet>(reinterpret_cast<IKeyRatchet *>(key_ratchet)));
}

void kyoko_dave_encryptor_set_passthrough_mode(kyoko_dave_encryptor *encryptor,
                                               bool passthrough_mode) {
  toEncryptor(encryptor)->SetPassthroughMode(passthrough_mode);
}

void kyoko_dave_encryptor_assign_ssrc_to_codec(kyoko_dave_encryptor *encryptor,
                                               uint32_t ssrc, int32_t codec) {
  toEncryptor(encryptor)->AssignSsrcToCodec(ssrc, static_cast<Codec>(codec));
}

uint16_t
kyoko_dave_encryptor_get_protocol_version(kyoko_dave_encryptor *encryptor) {
  return toEncryptor(encryptor)->GetProtocolVersion();
}

size_t kyoko_dave_encryptor_get_max_ciphertext_byte_size(
    kyoko_dave_encryptor *encryptor, int32_t media_type, size_t frame_size) {
  return toEncryptor(encryptor)->GetMaxCiphertextByteSize(
      static_cast<MediaType>(media_type), frame_size);
}

int32_t kyoko_dave_encryptor_encrypt(kyoko_dave_encryptor *encryptor,
                                     int32_t media_type, uint32_t ssrc,
                                     const uint8_t *frame, size_t frame_size,
                                     uint8_t *encrypted_frame,
                                     size_t encrypted_frame_capacity) {
  size_t bytesWritten = 0;
  auto result = toEncryptor(encryptor)->Encrypt(
      static_cast<MediaType>(media_type), ssrc,
      MakeArrayView(frame, frame_size),
      MakeArrayView(encrypted_frame, encrypted_frame_capacity), &bytesWritten);

  return mapEncryptorResult(result, bytesWritten);
}

void kyoko_dave_encryptor_set_protocol_version_changed_callback(
    kyoko_dave_encryptor *encryptor,
    kyoko_dave_protocol_version_changed_callback callback, void *user_data,
    kyoko_dave_user_data_free user_data_free) {
  auto holder = makeCallbackHolder(callback, user_data, user_data_free);
  if (!*holder) {
    toEncryptor(encryptor)->SetProtocolVersionChangedCallback(nullptr);
    return;
  }

  toEncryptor(encryptor)->SetProtocolVersionChangedCallback(
      [holder]() { (*holder)(); });
}

JNIEXPORT jlong JNICALL
Java_moe_kyokobot_libdave_natives_DaveNativeBindings_daveEncryptorCreate(
    JNIEnv *env, jobject clazz) {
  return reinterpret_cast<jlong>(kyoko_dave_encryptor_create());
}

JNIEXPORT void JNICALL
Java_moe_kyokobot_libdave_natives_DaveNativeBindings_daveEncryptorDestroy(
    JNIEnv *env, jobject clazz, jlong handle) {
  kyoko_dave_encryptor_destroy(fromHandle(handle));
}

JNIEXPORT void JNICALL
Java_moe_kyokobot_libdave_natives_DaveNativeBindings_daveEncryptorSetKeyRatchet(
    JNIEnv *env, jobject clazz, jlong handle, jlong keyRatchetHandle) {
  kyoko_dave_encryptor_set_key_ratchet(
      fromHandle(handle),
      reinterpret_cast<kyoko_dave_key_ratchet *>(keyRatchetHandle));
}

JNIEXPORT void JNICALL
Java_moe_kyokobot_libdave_natives_DaveNativeBindings_daveEncryptorSetPassthroughMode(
    JNIEnv *env, jobject clazz, jlong handle, jboolean passthroughMode) {
  kyoko_dave_encryptor_set_passthrough_mode(fromHandle(handle),
                                            passthroughMode);
}

JNIEXPORT void JNICALL
Java_moe_kyokobot_libdave_natives_DaveNativeBindings_daveEncryptorAssignSsrcToCodec(
    JNIEnv *env, jobject clazz, jlong handle, jint ssrc, jint codec) {
  kyoko_dave_encryptor_assign_ssrc_to_codec(
      fromHandle(handle), static_cast<uint32_t>(ssrc), codec);
}

JNIEXPORT jint JNICALL
Java_moe_kyokobot_libdave_natives_DaveNativeBindings_daveEncryptorGetProtocolVersion(
    JNIEnv *env, jobject clazz, jlong handle) {
  return static_cast<jint>(
      kyoko_dave_encryptor_get_protocol_version(fromHandle(handle)));
}

JNIEXPORT jlong JNICALL
Java_moe_kyokobot_libdave_natives_DaveNativeBindings_daveEncryptorGetMaxCiphertextByteSize(
    JNIEnv *env, jobject clazz, jlong handle, jint mediaType, jlong frameSize) {
  return static_cast<jlong>(kyoko_dave_encryptor_get_max_ciphertext_byte_size(
      fromHandle(handle), mediaType, static_cast<size_t>(frameSize)));
}

JNIEXPORT jint JNICALL
Java_moe_kyokobot_libdave_natives_DaveNativeBindings_daveEncryptorEncrypt__JII_3B_3B(
    JNIEnv *env, jobject clazz, jlong handle, jint mediaType, jint ssrc,
    jbyteArray frame, jbyteArray encryptedFrame) {
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

  auto result = kyoko_dave_encryptor_encrypt(
      fromHandle(handle), mediaType, static_cast<uint32_t>(ssrc),
      reinterpret_cast<const uint8_t *>(frameBytes),
      static_cast<size_t>(frameLen),
      reinterpret_cast<uint8_t *>(encryptedFrameBytes),
      static_cast<size_t>(encryptedFrameLen));

  env->ReleaseByteArrayElements(frame, frameBytes, JNI_ABORT);
  // Commit changes to encryptedFrame
  env->ReleaseByteArrayElements(encryptedFrame, encryptedFrameBytes, 0);

  return result;
}

JNIEXPORT jint JNICALL
Java_moe_kyokobot_libdave_natives_DaveNativeBindings_daveEncryptorEncrypt__JIILjava_nio_ByteBuffer_2Ljava_nio_ByteBuffer_2(
    JNIEnv *env, jobject clazz, jlong handle, jint mediaType, jint ssrc,
    jobject frame, jobject encryptedFrame) {
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

  return kyoko_dave_encryptor_encrypt(
      fromHandle(handle), mediaType, static_cast<uint32_t>(ssrc),
      frameInfo.address, frameInfo.length, encryptedFrameInfo.address,
      encryptedFrameInfo.length);
}

JNIEXPORT jint JNICALL
Java_moe_kyokobot_libdave_natives_DaveNativeBindings_daveEncryptorEncrypt__JIIJIJI(
    JNIEnv *env, jobject clazz, jlong handle, jint mediaType, jint ssrc,
    jlong framePtr, jint frameSize, jlong encryptedFramePtr,
    jint encryptedFrameCapacity) {
  if (framePtr == 0 || encryptedFramePtr == 0 || frameSize < 0 ||
      encryptedFrameCapacity < 0) {
    throwIllegalArgument(env, "Invalid frame pointer or size");
    return -1;
  }

  return kyoko_dave_encryptor_encrypt(
      fromHandle(handle), mediaType, static_cast<uint32_t>(ssrc),
      reinterpret_cast<const uint8_t *>(framePtr),
      static_cast<size_t>(frameSize),
      reinterpret_cast<uint8_t *>(encryptedFramePtr),
      static_cast<size_t>(encryptedFrameCapacity));
}

JNIEXPORT void JNICALL
Java_moe_kyokobot_libdave_natives_DaveNativeBindings_daveEncryptorSetProtocolVersionChangedCallback(
    JNIEnv *env, jobject clazz, jlong handle, jobject callback) {
  auto wrapper = newCallbackWrapper(env, callback, "onChanged", "()V");
  if (wrapper == nullptr) {
    kyoko_dave_encryptor_set_protocol_version_changed_callback(
        fromHandle(handle), nullptr, nullptr, nullptr);
    return;
  }

  kyoko_dave_encryptor_set_protocol_version_changed_callback(
      fromHandle(handle), JvmProtocolVersionChangedCallback, wrapper,
      deleteCallbackWrapper);
}
