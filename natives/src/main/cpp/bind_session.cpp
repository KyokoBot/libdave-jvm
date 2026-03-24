#include "jni_utils.h"
#include "moe_kyokobot_libdave_natives_DaveNativeBindings.h"
#include <dave/dave.h>
#include <dave/dave_interfaces.h>
#include <set>
#include <variant>

using namespace kyoko::libdave;
using namespace discord::dave;

namespace {
constexpr jint COMMIT_RESULT_FAILED = -1;
constexpr jint COMMIT_RESULT_IGNORED = -2;

constexpr const char *ROSTER_MAP_CLASS_NAME = "moe/kyokobot/libdave/RosterMap";

jobject toJavaRosterMap(JNIEnv *env,
                        const discord::dave::RosterMap &rosterMap) {
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
  jsize size = static_cast<jsize>(rosterMap.size());
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
  for (const auto &[userId, keyData] : rosterMap) {
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

JNIEXPORT jint JNICALL
Java_moe_kyokobot_libdave_natives_DaveNativeBindings_daveMaxSupportedProtocolVersion(
    JNIEnv *env, jobject clazz) {
  return daveMaxSupportedProtocolVersion();
}

JNIEXPORT jlong JNICALL
Java_moe_kyokobot_libdave_natives_DaveNativeBindings_daveSessionCreate(
    JNIEnv *env, jobject clazz, jstring context, jstring authSessionId,
    jobject callback) {
  const char *contextStr = env->GetStringUTFChars(context, nullptr);
  const char *authStr = env->GetStringUTFChars(authSessionId, nullptr);

  auto callbackWrapper = std::make_shared<JNICallbackWrapper>(
      env, callback, "onFailure", "(Ljava/lang/String;Ljava/lang/String;)V");

  auto cppCallback = [callbackWrapper](const std::string &source,
                                       const std::string &reason) {
    if (callbackWrapper && callbackWrapper->isValid()) {
      callbackWrapper->invoke(source, reason);
    }
  };

  auto contextType = static_cast<mls::KeyPairContextType>(JNI_KEY_PAIR_CONTEXT(env, contextStr));
  auto authSessionIdStr = authStr ? std::string(authStr) : std::string();

  auto session = mls::CreateSession(contextType, authSessionIdStr, cppCallback);

  env->ReleaseStringUTFChars(context, contextStr);
  env->ReleaseStringUTFChars(authSessionId, authStr);

  return reinterpret_cast<jlong>(session.release());
}

JNIEXPORT void JNICALL
Java_moe_kyokobot_libdave_natives_DaveNativeBindings_daveSessionDestroy(
    JNIEnv *env, jobject clazz, jlong sessionHandle) {
  auto session = reinterpret_cast<mls::ISession *>(sessionHandle);
  delete session;
}

JNIEXPORT void JNICALL
Java_moe_kyokobot_libdave_natives_DaveNativeBindings_daveSessionInit(
    JNIEnv *env, jobject clazz, jlong sessionHandle, jint version,
    jlong groupId, jstring selfUserId) {
  auto session = reinterpret_cast<mls::ISession *>(sessionHandle);
  auto selfUserIdStr = env->GetStringUTFChars(selfUserId, nullptr);

  auto versionC = static_cast<uint16_t>(version);
  auto groupIdC = static_cast<uint64_t>(groupId);
  auto selfUserIdStrC =
      selfUserIdStr ? std::string(selfUserIdStr) : std::string();
  std::shared_ptr<::mlspp::SignaturePrivateKey>
      transientKey; // TODO: add bindings for this?

  session->Init(versionC, groupIdC, selfUserIdStrC, transientKey);

  env->ReleaseStringUTFChars(selfUserId, selfUserIdStr);
}

JNIEXPORT void JNICALL
Java_moe_kyokobot_libdave_natives_DaveNativeBindings_daveSessionReset(
    JNIEnv *env, jobject clazz, jlong sessionHandle) {
  auto session = reinterpret_cast<mls::ISession *>(sessionHandle);
  session->Reset();
}

JNIEXPORT void JNICALL
Java_moe_kyokobot_libdave_natives_DaveNativeBindings_daveSessionSetProtocolVersion(
    JNIEnv *env, jobject clazz, jlong sessionHandle, jint version) {
  auto session = reinterpret_cast<mls::ISession *>(sessionHandle);
  auto versionC = static_cast<uint16_t>(version);
  session->SetProtocolVersion(versionC);
}

JNIEXPORT jint JNICALL
Java_moe_kyokobot_libdave_natives_DaveNativeBindings_daveSessionGetProtocolVersion(
    JNIEnv *env, jobject clazz, jlong sessionHandle) {
  auto session = reinterpret_cast<mls::ISession *>(sessionHandle);
  return static_cast<jint>(session->GetProtocolVersion());
}

JNIEXPORT jbyteArray JNICALL
Java_moe_kyokobot_libdave_natives_DaveNativeBindings_daveSessionGetLastEpochAuthenticator(
    JNIEnv *env, jobject clazz, jlong sessionHandle) {
  auto session = reinterpret_cast<mls::ISession *>(sessionHandle);
  auto lastEpochAuthenticator = session->GetLastEpochAuthenticator();
  auto array = toByteArray(env, lastEpochAuthenticator);
  return array;
}

JNIEXPORT void JNICALL
Java_moe_kyokobot_libdave_natives_DaveNativeBindings_daveSessionSetExternalSender(
    JNIEnv *env, jobject clazz, jlong sessionHandle,
    jbyteArray externalSender) {
  auto session = reinterpret_cast<mls::ISession *>(sessionHandle);
  std::vector<uint8_t> externalSenderVec;
  if (!copyByteArrayToVector(env, externalSender, externalSenderVec)) {
    throwIllegalArgument(env, "Failed to read external sender");
    return;
  }
  session->SetExternalSender(externalSenderVec);
}

JNIEXPORT jbyteArray JNICALL
Java_moe_kyokobot_libdave_natives_DaveNativeBindings_daveSessionProcessProposals(
    JNIEnv *env, jobject clazz, jlong sessionHandle, jbyteArray proposals,
    jobjectArray recognizedUserIds) {
  auto session = reinterpret_cast<mls::ISession *>(sessionHandle);
  std::vector<uint8_t> proposalsVec;
  if (!copyByteArrayToVector(env, proposals, proposalsVec)) {
    throwIllegalArgument(env, "Failed to read proposals");
    return nullptr;
  }

  std::set<std::string> recognizedUserIdsSet;
  auto userCount = env->GetArrayLength(recognizedUserIds);
  for (jsize i = 0; i < userCount; ++i) {
    LocalRefHolder<1> loopHolder(env);
    auto jstr = (jstring)loopHolder.track(
        env->GetObjectArrayElement(recognizedUserIds, i));
    const char *utfChars = env->GetStringUTFChars(jstr, nullptr);
    if (utfChars == nullptr) {
      throwIllegalArgument(env, "Failed to read a recognized user ID");
      return nullptr;
    }

    recognizedUserIdsSet.insert(std::string(utfChars));

    env->ReleaseStringUTFChars(jstr, utfChars);
  }

  auto result = session->ProcessProposals(proposalsVec, recognizedUserIdsSet);
  if (result) {
    return toByteArray(env, *result);
  }

  return nullptr;
}

JNIEXPORT jobject JNICALL
Java_moe_kyokobot_libdave_natives_DaveNativeBindings_daveSessionProcessCommit(
    JNIEnv *env, jobject clazz, jlong sessionHandle, jbyteArray commit) {
  auto session = reinterpret_cast<mls::ISession *>(sessionHandle);
  std::vector<uint8_t> commitVec;

  if (!copyByteArrayToVector(env, commit, commitVec)) {
    throwIllegalArgument(env, "Failed to read commit");
    return nullptr;
  }

  auto result = session->ProcessCommit(commitVec);

  if (std::holds_alternative<failed_t>(result)) {
    return boxedInteger(env, COMMIT_RESULT_FAILED);
  }

  if (std::holds_alternative<ignored_t>(result)) {
    return boxedInteger(env, COMMIT_RESULT_IGNORED);
  }

  const auto &rosterMap = std::get<RosterMap>(result);

  return toJavaRosterMap(env, rosterMap);
}

JNIEXPORT jobject JNICALL
Java_moe_kyokobot_libdave_natives_DaveNativeBindings_daveSessionProcessWelcome(
    JNIEnv *env, jobject clazz, jlong sessionHandle, jbyteArray welcome,
    jobjectArray recognizedUserIds) {
  auto session = reinterpret_cast<mls::ISession *>(sessionHandle);
  std::vector<uint8_t> welcomeVec;
  if (!copyByteArrayToVector(env, welcome, welcomeVec)) {
    throwIllegalArgument(env, "Failed to read welcome");
    return nullptr;
  }

  std::set<std::string> recognizedUserIdsSet;
  jsize len = env->GetArrayLength(recognizedUserIds);
  for (jsize i = 0; i < len; ++i) {
    LocalRefHolder<1> loopHolder(env);
    jstring jstr = (jstring)loopHolder.track(
        env->GetObjectArrayElement(recognizedUserIds, i));
    if (jstr == nullptr)
      continue;
    const char *utfChars = env->GetStringUTFChars(jstr, nullptr);
    if (utfChars) {
      recognizedUserIdsSet.insert(std::string(utfChars));
      env->ReleaseStringUTFChars(jstr, utfChars);
    }
  }

  auto rosterMap = session->ProcessWelcome(welcomeVec, recognizedUserIdsSet);
  if (!rosterMap) {
    // Return null on failure
    return nullptr;
  }

  return toJavaRosterMap(env, *rosterMap);
}

JNIEXPORT jbyteArray JNICALL
Java_moe_kyokobot_libdave_natives_DaveNativeBindings_daveSessionGetMarshalledKeyPackage(
    JNIEnv *env, jobject clazz, jlong sessionHandle) {
  auto session = reinterpret_cast<mls::ISession *>(sessionHandle);
  auto keyPackage = session->GetMarshalledKeyPackage();
  return toByteArray(env, keyPackage);
}

JNIEXPORT jlong JNICALL
Java_moe_kyokobot_libdave_natives_DaveNativeBindings_daveSessionGetKeyRatchet(
    JNIEnv *env, jobject clazz, jlong sessionHandle, jstring userId) {
  auto session = reinterpret_cast<mls::ISession *>(sessionHandle);
  auto userIdStr = env->GetStringUTFChars(userId, nullptr);
  auto keyRatchet = session->GetKeyRatchet(userIdStr);
  return reinterpret_cast<jlong>(keyRatchet.release());
}

JNIEXPORT void JNICALL
Java_moe_kyokobot_libdave_natives_DaveNativeBindings_daveSessionGetPairwiseFingerprint(
    JNIEnv *env, jobject clazz, jlong sessionHandle, jint version,
    jstring userId, jobject callback) {
  auto session = reinterpret_cast<mls::ISession *>(sessionHandle);
  auto userIdStr = env->GetStringUTFChars(userId, nullptr);
  auto callbackWrapper = std::make_shared<JNICallbackWrapper>(
      env, callback, "accept", "(Ljava/lang/Object;)V");
  session->GetPairwiseFingerprint(
      version, userIdStr,
      [callbackWrapper](std::vector<uint8_t> const &fingerprint) {
        if (callbackWrapper && callbackWrapper->isValid()) {
          auto env = callbackWrapper->getEnv();
          callbackWrapper->invoke(toByteArray(env, fingerprint));
        }
      });
}
