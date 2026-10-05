#include "jni_utils.h"
#include "kyoko_dave_internal.h"
#include "moe_kyokobot_libdave_natives_DaveNativeBindings.h"
#include <dave/dave.h>
#include <dave/dave_interfaces.h>
#include <dave/version.h>
#include <set>
#include <utility>
#include <variant>

using namespace kyoko::libdave;
using namespace discord::dave;

struct kyoko_dave_roster {
  std::vector<std::pair<uint64_t, std::vector<uint8_t>>> entries;
};

namespace {
constexpr const char *ROSTER_MAP_CLASS_NAME = "moe/kyokobot/libdave/RosterMap";

// KeyPairContextType is a raw pointer whose pointee must outlive the session.
// The bindings pass an empty context, so hand out a shared interned string
// instead of a pointer into released JNI string memory.
const char *sessionContextStorage() {
  static const std::string *storage = new std::string();
  return storage->c_str();
}

mls::ISession *toSession(kyoko_dave_session *handle) {
  return reinterpret_cast<mls::ISession *>(handle);
}

kyoko_dave_session *fromHandle(jlong handle) {
  return reinterpret_cast<kyoko_dave_session *>(handle);
}

std::vector<uint8_t> toVector(const uint8_t *data, size_t size) {
  if (data == nullptr) {
    return {};
  }
  return std::vector<uint8_t>(data, data + size);
}

std::set<std::string> toUserIdSet(const char *const *userIds, size_t count) {
  std::set<std::string> userIdSet;
  for (size_t i = 0; i < count; ++i) {
    if (userIds[i] != nullptr) {
      userIdSet.insert(userIds[i]);
    }
  }
  return userIdSet;
}

kyoko_dave_roster *toRoster(RosterMap &&rosterMap) {
  auto roster = new kyoko_dave_roster();
  roster->entries.reserve(rosterMap.size());
  for (auto &[userId, keyData] : rosterMap) {
    roster->entries.emplace_back(userId, std::move(keyData));
  }
  return roster;
}

void JvmMLSFailureCallback(const char *source, const char *reason,
                           void *userData) {
  static_cast<JNICallbackWrapper *>(userData)->invoke(std::string(source),
                                                      std::string(reason));
}

void JvmPairwiseFingerprintCallback(const uint8_t *fingerprint, size_t size,
                                    void *userData) {
  static_cast<JNICallbackWrapper *>(userData)->invoke(fingerprint, size);
}

// Copies a String[] into userIds, skipping null elements. Returns false with a
// pending exception on failure.
bool readUserIds(JNIEnv *env, jobjectArray array,
                 std::vector<std::string> &userIds) {
  jsize length = env->GetArrayLength(array);
  userIds.reserve(length);
  for (jsize i = 0; i < length; ++i) {
    LocalRefHolder<1> loopHolder(env);
    auto jstr =
        loopHolder.track(static_cast<jstring>(env->GetObjectArrayElement(array, i)));
    if (jstr == nullptr) {
      continue;
    }

    ScopedUTFChars utfChars(env, jstr);
    if (utfChars.get() == nullptr) {
      throwIllegalArgument(env, "Failed to read a recognized user ID");
      return false;
    }
    userIds.emplace_back(utfChars.get());
  }
  return true;
}

std::vector<const char *> toCStrings(const std::vector<std::string> &strings) {
  std::vector<const char *> cStrings;
  cStrings.reserve(strings.size());
  for (const auto &string : strings) {
    cStrings.push_back(string.c_str());
  }
  return cStrings;
}

jbyteArray toByteArrayAndFree(JNIEnv *env, uint8_t *data, size_t size) {
  auto array = toByteArray(env, data, size);
  kyoko_dave_free(data);
  return array;
}

jobject toJavaRosterMap(JNIEnv *env, const kyoko_dave_roster *roster) {
  LocalRefHolder<8> holder(env);

  // Find the RosterMap class
  jclass rosterMapClass = holder.track(env->FindClass(ROSTER_MAP_CLASS_NAME));
  if (rosterMapClass == nullptr) {
    return nullptr;
  }

  // Find the constructor: RosterMap(long[] keys, byte[][] values)
  jmethodID constructor =
      env->GetMethodID(rosterMapClass, "<init>", "([J[[B)V");
  if (constructor == nullptr) {
    return nullptr;
  }

  // Create the keys array (long[])
  jsize size = static_cast<jsize>(roster->entries.size());
  jlongArray keysArray = holder.track(env->NewLongArray(size));
  if (keysArray == nullptr) {
    return nullptr;
  }

  // Create the values array (byte[][])
  jclass byteArrayClass = holder.track(env->FindClass("[B"));
  if (byteArrayClass == nullptr) {
    return nullptr;
  }

  jobjectArray valuesArray =
      holder.track(env->NewObjectArray(size, byteArrayClass, nullptr));
  if (valuesArray == nullptr) {
    return nullptr;
  }

  // Populate keys and values
  jsize index = 0;
  for (const auto &[userId, keyData] : roster->entries) {
    LocalRefHolder<2> loopHolder(env);

    // Set key
    jlong key = static_cast<jlong>(userId);
    env->SetLongArrayRegion(keysArray, index, 1, &key);

    // Set value (byte array)
    jbyteArray valueArray = loopHolder.track(toByteArray(env, keyData));
    if (valueArray == nullptr) {
      return nullptr;
    }
    env->SetObjectArrayElement(valuesArray, index, valueArray);

    index++;
  }

  // Create the RosterMap object
  return env->NewObject(rosterMapClass, constructor, keysArray, valuesArray);
}

} // namespace

void kyoko_dave_free(void *ptr) { std::free(ptr); }

uint16_t kyoko_dave_max_supported_protocol_version(void) {
  return MaxSupportedProtocolVersion();
}

kyoko_dave_session *
kyoko_dave_session_create(const char *auth_session_id,
                          kyoko_dave_mls_failure_callback callback,
                          void *user_data,
                          kyoko_dave_user_data_free user_data_free) {
  auto holder = makeCallbackHolder(callback, user_data, user_data_free);
  mls::MLSFailureCallback failureCallback;
  if (*holder) {
    failureCallback = [holder](const std::string &source,
                               const std::string &reason) {
      (*holder)(source.c_str(), reason.c_str());
    };
  }

  auto session = mls::CreateSession(
      static_cast<mls::KeyPairContextType>(sessionContextStorage()),
      auth_session_id != nullptr ? std::string(auth_session_id) : std::string(),
      std::move(failureCallback));
  return reinterpret_cast<kyoko_dave_session *>(session.release());
}

void kyoko_dave_session_destroy(kyoko_dave_session *session) {
  delete toSession(session);
}

void kyoko_dave_session_init(kyoko_dave_session *session, uint16_t version,
                             uint64_t group_id, const char *self_user_id) {
  std::shared_ptr<::mlspp::SignaturePrivateKey>
      transientKey; // TODO: add bindings for this?
  toSession(session)->Init(
      version, group_id,
      self_user_id != nullptr ? std::string(self_user_id) : std::string(),
      transientKey);
}

void kyoko_dave_session_reset(kyoko_dave_session *session) {
  toSession(session)->Reset();
}

void kyoko_dave_session_set_protocol_version(kyoko_dave_session *session,
                                             uint16_t version) {
  toSession(session)->SetProtocolVersion(version);
}

uint16_t kyoko_dave_session_get_protocol_version(kyoko_dave_session *session) {
  return toSession(session)->GetProtocolVersion();
}

void kyoko_dave_session_get_last_epoch_authenticator(
    kyoko_dave_session *session, uint8_t **out_data, size_t *out_size) {
  copyToOutputBuffer(toSession(session)->GetLastEpochAuthenticator(), out_data,
                     out_size);
}

void kyoko_dave_session_set_external_sender(kyoko_dave_session *session,
                                            const uint8_t *external_sender,
                                            size_t external_sender_size) {
  toSession(session)->SetExternalSender(
      toVector(external_sender, external_sender_size));
}

bool kyoko_dave_session_process_proposals(
    kyoko_dave_session *session, const uint8_t *proposals,
    size_t proposals_size, const char *const *recognized_user_ids,
    size_t recognized_user_ids_count, uint8_t **out_data, size_t *out_size) {
  auto result = toSession(session)->ProcessProposals(
      toVector(proposals, proposals_size),
      toUserIdSet(recognized_user_ids, recognized_user_ids_count));
  if (!result) {
    return false;
  }

  copyToOutputBuffer(*result, out_data, out_size);
  return true;
}

int32_t kyoko_dave_session_process_commit(kyoko_dave_session *session,
                                          const uint8_t *commit,
                                          size_t commit_size,
                                          kyoko_dave_roster **out_roster) {
  auto result =
      toSession(session)->ProcessCommit(toVector(commit, commit_size));

  if (std::holds_alternative<failed_t>(result)) {
    return KYOKO_DAVE_COMMIT_RESULT_FAILED;
  }

  if (std::holds_alternative<ignored_t>(result)) {
    return KYOKO_DAVE_COMMIT_RESULT_IGNORED;
  }

  if (out_roster != nullptr) {
    *out_roster = toRoster(std::get<RosterMap>(std::move(result)));
  }
  return KYOKO_DAVE_COMMIT_RESULT_SUCCESS;
}

kyoko_dave_roster *kyoko_dave_session_process_welcome(
    kyoko_dave_session *session, const uint8_t *welcome, size_t welcome_size,
    const char *const *recognized_user_ids, size_t recognized_user_ids_count) {
  auto rosterMap = toSession(session)->ProcessWelcome(
      toVector(welcome, welcome_size),
      toUserIdSet(recognized_user_ids, recognized_user_ids_count));
  if (!rosterMap) {
    return nullptr;
  }

  return toRoster(std::move(*rosterMap));
}

void kyoko_dave_session_get_marshalled_key_package(kyoko_dave_session *session,
                                                   uint8_t **out_data,
                                                   size_t *out_size) {
  copyToOutputBuffer(toSession(session)->GetMarshalledKeyPackage(), out_data,
                     out_size);
}

kyoko_dave_key_ratchet *
kyoko_dave_session_get_key_ratchet(kyoko_dave_session *session,
                                   const char *user_id) {
  auto keyRatchet = toSession(session)->GetKeyRatchet(
      user_id != nullptr ? std::string(user_id) : std::string());
  return reinterpret_cast<kyoko_dave_key_ratchet *>(keyRatchet.release());
}

void kyoko_dave_session_get_pairwise_fingerprint(
    kyoko_dave_session *session, uint16_t version, const char *user_id,
    kyoko_dave_pairwise_fingerprint_callback callback, void *user_data,
    kyoko_dave_user_data_free user_data_free) {
  auto holder = makeCallbackHolder(callback, user_data, user_data_free);
  toSession(session)->GetPairwiseFingerprint(
      version, user_id != nullptr ? std::string(user_id) : std::string(),
      [holder](std::vector<uint8_t> const &fingerprint) {
        (*holder)(fingerprint.data(), fingerprint.size());
      });
}

size_t kyoko_dave_roster_size(const kyoko_dave_roster *roster) {
  return roster->entries.size();
}

uint64_t kyoko_dave_roster_get_user_id(const kyoko_dave_roster *roster,
                                       size_t index) {
  return roster->entries[index].first;
}

size_t kyoko_dave_roster_get_key_size(const kyoko_dave_roster *roster,
                                      size_t index) {
  return roster->entries[index].second.size();
}

const uint8_t *kyoko_dave_roster_get_key_data(const kyoko_dave_roster *roster,
                                              size_t index) {
  return roster->entries[index].second.data();
}

void kyoko_dave_roster_destroy(kyoko_dave_roster *roster) { delete roster; }

JNIEXPORT jint JNICALL
Java_moe_kyokobot_libdave_natives_DaveNativeBindings_daveMaxSupportedProtocolVersion(
    JNIEnv *env, jobject clazz) {
  return kyoko_dave_max_supported_protocol_version();
}

JNIEXPORT jlong JNICALL
Java_moe_kyokobot_libdave_natives_DaveNativeBindings_daveSessionCreate(
    JNIEnv *env, jobject clazz, jstring context, jstring authSessionId,
    jobject callback) {
  // The context is unused: persisted keys are not supported by the bindings.
  (void)context;

  ScopedUTFChars authStr(env, authSessionId);
  if (authSessionId != nullptr && authStr.get() == nullptr) {
    return 0; // pending exception
  }

  auto wrapper = newCallbackWrapper(env, callback, "onFailure",
                                    "(Ljava/lang/String;Ljava/lang/String;)V");
  auto session = kyoko_dave_session_create(
      authStr.get(), wrapper != nullptr ? JvmMLSFailureCallback : nullptr,
      wrapper, wrapper != nullptr ? deleteCallbackWrapper : nullptr);

  return reinterpret_cast<jlong>(session);
}

JNIEXPORT void JNICALL
Java_moe_kyokobot_libdave_natives_DaveNativeBindings_daveSessionDestroy(
    JNIEnv *env, jobject clazz, jlong sessionHandle) {
  kyoko_dave_session_destroy(fromHandle(sessionHandle));
}

JNIEXPORT void JNICALL
Java_moe_kyokobot_libdave_natives_DaveNativeBindings_daveSessionInit(
    JNIEnv *env, jobject clazz, jlong sessionHandle, jint version,
    jlong groupId, jstring selfUserId) {
  ScopedUTFChars selfUserIdStr(env, selfUserId);
  if (selfUserId != nullptr && selfUserIdStr.get() == nullptr) {
    return; // pending exception
  }

  kyoko_dave_session_init(fromHandle(sessionHandle),
                          static_cast<uint16_t>(version),
                          static_cast<uint64_t>(groupId), selfUserIdStr.get());
}

JNIEXPORT void JNICALL
Java_moe_kyokobot_libdave_natives_DaveNativeBindings_daveSessionReset(
    JNIEnv *env, jobject clazz, jlong sessionHandle) {
  kyoko_dave_session_reset(fromHandle(sessionHandle));
}

JNIEXPORT void JNICALL
Java_moe_kyokobot_libdave_natives_DaveNativeBindings_daveSessionSetProtocolVersion(
    JNIEnv *env, jobject clazz, jlong sessionHandle, jint version) {
  kyoko_dave_session_set_protocol_version(fromHandle(sessionHandle),
                                          static_cast<uint16_t>(version));
}

JNIEXPORT jint JNICALL
Java_moe_kyokobot_libdave_natives_DaveNativeBindings_daveSessionGetProtocolVersion(
    JNIEnv *env, jobject clazz, jlong sessionHandle) {
  return static_cast<jint>(
      kyoko_dave_session_get_protocol_version(fromHandle(sessionHandle)));
}

JNIEXPORT jbyteArray JNICALL
Java_moe_kyokobot_libdave_natives_DaveNativeBindings_daveSessionGetLastEpochAuthenticator(
    JNIEnv *env, jobject clazz, jlong sessionHandle) {
  uint8_t *data = nullptr;
  size_t size = 0;
  kyoko_dave_session_get_last_epoch_authenticator(fromHandle(sessionHandle),
                                                  &data, &size);
  return toByteArrayAndFree(env, data, size);
}

JNIEXPORT void JNICALL
Java_moe_kyokobot_libdave_natives_DaveNativeBindings_daveSessionSetExternalSender(
    JNIEnv *env, jobject clazz, jlong sessionHandle,
    jbyteArray externalSender) {
  std::vector<uint8_t> externalSenderVec;
  if (!copyByteArrayToVector(env, externalSender, externalSenderVec)) {
    throwIllegalArgument(env, "Failed to read external sender");
    return;
  }
  kyoko_dave_session_set_external_sender(fromHandle(sessionHandle),
                                         externalSenderVec.data(),
                                         externalSenderVec.size());
}

JNIEXPORT jbyteArray JNICALL
Java_moe_kyokobot_libdave_natives_DaveNativeBindings_daveSessionProcessProposals(
    JNIEnv *env, jobject clazz, jlong sessionHandle, jbyteArray proposals,
    jobjectArray recognizedUserIds) {
  std::vector<uint8_t> proposalsVec;
  if (!copyByteArrayToVector(env, proposals, proposalsVec)) {
    throwIllegalArgument(env, "Failed to read proposals");
    return nullptr;
  }

  std::vector<std::string> userIds;
  if (!readUserIds(env, recognizedUserIds, userIds)) {
    return nullptr; // pending exception
  }
  auto userIdPtrs = toCStrings(userIds);

  uint8_t *data = nullptr;
  size_t size = 0;
  if (!kyoko_dave_session_process_proposals(
          fromHandle(sessionHandle), proposalsVec.data(), proposalsVec.size(),
          userIdPtrs.data(), userIdPtrs.size(), &data, &size)) {
    return nullptr;
  }

  return toByteArrayAndFree(env, data, size);
}

JNIEXPORT jobject JNICALL
Java_moe_kyokobot_libdave_natives_DaveNativeBindings_daveSessionProcessCommit(
    JNIEnv *env, jobject clazz, jlong sessionHandle, jbyteArray commit) {
  std::vector<uint8_t> commitVec;
  if (!copyByteArrayToVector(env, commit, commitVec)) {
    throwIllegalArgument(env, "Failed to read commit");
    return nullptr;
  }

  kyoko_dave_roster *roster = nullptr;
  auto result = kyoko_dave_session_process_commit(
      fromHandle(sessionHandle), commitVec.data(), commitVec.size(), &roster);
  if (result != KYOKO_DAVE_COMMIT_RESULT_SUCCESS) {
    return boxedInteger(env, result);
  }

  auto rosterMap = toJavaRosterMap(env, roster);
  kyoko_dave_roster_destroy(roster);
  return rosterMap;
}

JNIEXPORT jobject JNICALL
Java_moe_kyokobot_libdave_natives_DaveNativeBindings_daveSessionProcessWelcome(
    JNIEnv *env, jobject clazz, jlong sessionHandle, jbyteArray welcome,
    jobjectArray recognizedUserIds) {
  std::vector<uint8_t> welcomeVec;
  if (!copyByteArrayToVector(env, welcome, welcomeVec)) {
    throwIllegalArgument(env, "Failed to read welcome");
    return nullptr;
  }

  std::vector<std::string> userIds;
  if (!readUserIds(env, recognizedUserIds, userIds)) {
    return nullptr; // pending exception
  }
  auto userIdPtrs = toCStrings(userIds);

  auto roster = kyoko_dave_session_process_welcome(
      fromHandle(sessionHandle), welcomeVec.data(), welcomeVec.size(),
      userIdPtrs.data(), userIdPtrs.size());
  if (roster == nullptr) {
    // Return null on failure
    return nullptr;
  }

  auto rosterMap = toJavaRosterMap(env, roster);
  kyoko_dave_roster_destroy(roster);
  return rosterMap;
}

JNIEXPORT jbyteArray JNICALL
Java_moe_kyokobot_libdave_natives_DaveNativeBindings_daveSessionGetMarshalledKeyPackage(
    JNIEnv *env, jobject clazz, jlong sessionHandle) {
  uint8_t *data = nullptr;
  size_t size = 0;
  kyoko_dave_session_get_marshalled_key_package(fromHandle(sessionHandle),
                                                &data, &size);
  return toByteArrayAndFree(env, data, size);
}

JNIEXPORT jlong JNICALL
Java_moe_kyokobot_libdave_natives_DaveNativeBindings_daveSessionGetKeyRatchet(
    JNIEnv *env, jobject clazz, jlong sessionHandle, jstring userId) {
  ScopedUTFChars userIdStr(env, userId);
  if (userId != nullptr && userIdStr.get() == nullptr) {
    return 0; // pending exception
  }
  return reinterpret_cast<jlong>(kyoko_dave_session_get_key_ratchet(
      fromHandle(sessionHandle), userIdStr.get()));
}

JNIEXPORT void JNICALL
Java_moe_kyokobot_libdave_natives_DaveNativeBindings_daveSessionGetPairwiseFingerprint(
    JNIEnv *env, jobject clazz, jlong sessionHandle, jint version,
    jstring userId, jobject callback) {
  ScopedUTFChars userIdStr(env, userId);
  if (userId != nullptr && userIdStr.get() == nullptr) {
    return; // pending exception
  }

  auto wrapper =
      newCallbackWrapper(env, callback, "accept", "(Ljava/lang/Object;)V");
  if (wrapper == nullptr) {
    return;
  }

  kyoko_dave_session_get_pairwise_fingerprint(
      fromHandle(sessionHandle), static_cast<uint16_t>(version),
      userIdStr.get(), JvmPairwiseFingerprintCallback, wrapper,
      deleteCallbackWrapper);
}
