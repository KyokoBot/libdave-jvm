#include "jni_utils.h"
#include "kyoko_dave_internal.h"
#include "moe_kyokobot_libdave_natives_DaveNativeBindings.h"
#include <bytes/bytes.h>
#include <dave/dave.h>
#include <dave/dave_interfaces.h>

using namespace kyoko::libdave;
using namespace discord::dave;

namespace {
IKeyRatchet *toKeyRatchet(kyoko_dave_key_ratchet *handle) {
  return reinterpret_cast<IKeyRatchet *>(handle);
}

kyoko_dave_key_ratchet *fromHandle(jlong handle) {
  return reinterpret_cast<kyoko_dave_key_ratchet *>(handle);
}
} // namespace

void kyoko_dave_key_ratchet_get_encryption_key(
    kyoko_dave_key_ratchet *key_ratchet, uint32_t key_generation,
    uint8_t **out_data, size_t *out_size) {
  auto key = toKeyRatchet(key_ratchet)->GetKey(key_generation);
  copyToOutputBuffer(key.as_vec(), out_data, out_size);
}

void kyoko_dave_key_ratchet_delete_key(kyoko_dave_key_ratchet *key_ratchet,
                                       uint32_t key_generation) {
  toKeyRatchet(key_ratchet)->DeleteKey(key_generation);
}

void kyoko_dave_key_ratchet_destroy(kyoko_dave_key_ratchet *key_ratchet) {
  delete toKeyRatchet(key_ratchet);
}

JNIEXPORT jbyteArray JNICALL
Java_moe_kyokobot_libdave_natives_DaveNativeBindings_daveKeyRatchetGetEncryptionKey(
    JNIEnv *env, jobject clazz, jlong handle, jint keyGeneration) {
  uint8_t *key = nullptr;
  size_t keySize = 0;
  kyoko_dave_key_ratchet_get_encryption_key(
      fromHandle(handle), static_cast<uint32_t>(keyGeneration), &key, &keySize);
  auto array = toByteArray(env, key, keySize);
  kyoko_dave_free(key);
  return array;
}

JNIEXPORT void JNICALL
Java_moe_kyokobot_libdave_natives_DaveNativeBindings_daveKeyRatchetDeleteKey(
    JNIEnv *env, jobject clazz, jlong handle, jint keyGeneration) {
  kyoko_dave_key_ratchet_delete_key(fromHandle(handle),
                                    static_cast<uint32_t>(keyGeneration));
}

JNIEXPORT void JNICALL
Java_moe_kyokobot_libdave_natives_DaveNativeBindings_daveKeyRatchetDestroy(
    JNIEnv *env, jobject clazz, jlong handle) {
  kyoko_dave_key_ratchet_destroy(fromHandle(handle));
}
