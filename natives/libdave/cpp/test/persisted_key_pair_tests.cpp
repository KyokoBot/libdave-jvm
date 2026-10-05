#include <gtest/gtest.h>

#include <cstdlib>
#include <filesystem>
#include <fstream>
#include <memory>
#include <optional>
#include <string>
#include <utility>
#include <vector>

#include <dave/version.h>
#include <mls/crypto.h>
#include <mls/messages.h>

#include "dave_test.h"
#include "mls/parameters.h"
#include "mls/persisted_key_pair.h"
#include "mls/session.h"

namespace discord {
namespace dave {
namespace test {

// The keychain backends report failure by throwing; the wrapper must swallow and return empty.
static_assert(noexcept(mls::GetPersistedPublicKey(std::declval<mls::KeyPairContextType>(),
                                                  std::declval<std::string const&>(),
                                                  std::declval<SignatureVersion>())),
              "GetPersistedPublicKey must be noexcept");

// On these platforms the persisted-key backend is the generic file store, whose location
// honors XDG_CONFIG_HOME, so tests can point key storage at a scratch directory or somewhere
// unusable. The Apple keychain, Windows NCrypt store, and Android (path derived from
// /proc/self/cmdline) cannot be redirected off-device.
#if !defined(__APPLE__) && !defined(_WIN32) && !defined(__ANDROID__)
#define DAVE_TEST_REDIRECTABLE_KEY_STORAGE 1
#endif

// The session tests need GetPersistedKeyPair to fail on demand: without PERSISTENT_KEYS the
// null implementation always fails, and redirectable storage can be made unavailable.
#if !defined(DAVE_PERSISTENT_KEYS) || defined(DAVE_TEST_REDIRECTABLE_KEY_STORAGE)
#define DAVE_TEST_SESSION_FALLBACK 1
#endif

#if defined(DAVE_TEST_SESSION_FALLBACK)

namespace {

::mlspp::KeyPackage UnmarshalKeyPackage(std::vector<uint8_t> const& marshalled)
{
    return ::mlspp::tls::get<::mlspp::KeyPackage>(marshalled);
}

// A failed persisted-key lookup must report through MLSFailureCallback and fall back to a
// transient signing key instead of aborting leaf-node initialization.
void ExpectSessionFallsBackToTransientKey()
{
    std::vector<std::pair<std::string, std::string>> failures;
    mls::Session session(
      nullptr, "fallback-session", [&](std::string const& source, std::string const& reason) {
          failures.emplace_back(source, reason);
      });

    std::shared_ptr<::mlspp::SignaturePrivateKey> transientKey;
    session.Init(MaxSupportedProtocolVersion(), 1234, "170920224557176832", transientKey);

    auto marshalled = session.GetMarshalledKeyPackage();
    ASSERT_FALSE(marshalled.empty());

    ASSERT_EQ(failures.size(), 1u);
    EXPECT_EQ(failures[0].first, "GetPersistedKeyPair");
    EXPECT_FALSE(failures[0].second.empty());

    ASSERT_NE(transientKey, nullptr);
    auto keyPackage = UnmarshalKeyPackage(marshalled);
    EXPECT_EQ(keyPackage.leaf_node.signature_key.data, transientKey->public_key.data);
}

// A signing key resolved by an earlier Init must not shadow the caller-supplied transient key.
void ExpectSessionHonorsSuppliedTransientKeyOnReinit()
{
    mls::Session session(nullptr, "reinit-session", {});

    std::shared_ptr<::mlspp::SignaturePrivateKey> firstKey;
    session.Init(MaxSupportedProtocolVersion(), 1234, "170920224557176832", firstKey);

    auto suppliedKey =
      std::make_shared<::mlspp::SignaturePrivateKey>(::mlspp::SignaturePrivateKey::generate(
        mls::CiphersuiteForProtocolVersion(MaxSupportedProtocolVersion())));
    auto transientKey = suppliedKey;
    session.Init(MaxSupportedProtocolVersion(), 1234, "170920224557176832", transientKey);

    auto marshalled = session.GetMarshalledKeyPackage();
    ASSERT_FALSE(marshalled.empty());

    auto keyPackage = UnmarshalKeyPackage(marshalled);
    EXPECT_EQ(keyPackage.leaf_node.signature_key.data, suppliedKey->public_key.data);
}

} // namespace

#endif // defined(DAVE_TEST_SESSION_FALLBACK)

#if defined(DAVE_PERSISTENT_KEYS) && defined(DAVE_TEST_REDIRECTABLE_KEY_STORAGE)

namespace {

constexpr const char* KeyStorageEnvVar = "XDG_CONFIG_HOME";
constexpr SignatureVersion TestSignatureVersion = 0;

} // namespace

// GetPersistedKeyPair memoizes per key ID for the process lifetime; each test needs a unique
// session ID.
class PersistedKeyPairTests : public DaveTests {
protected:
    void SetUp() override
    {
        if (const char* original = getenv(KeyStorageEnvVar)) {
            originalStorageEnv_ = original;
        }

        baseDir_ = std::filesystem::temp_directory_path() / "libdave-persisted-key-tests";
        std::filesystem::remove_all(baseDir_);

        writableDir_ = baseDir_ / "storage";
        std::filesystem::create_directories(writableDir_);

        // A regular file makes every path beneath it unusable as a storage directory.
        blockerFile_ = baseDir_ / "blocker";
        std::ofstream(blockerFile_).put('x');
    }

    void TearDown() override
    {
        if (originalStorageEnv_) {
            setenv(KeyStorageEnvVar, originalStorageEnv_->c_str(), 1);
        }
        else {
            unsetenv(KeyStorageEnvVar);
        }

        std::filesystem::remove_all(baseDir_);
    }

    void UseWritableStorage() { setenv(KeyStorageEnvVar, writableDir_.c_str(), 1); }
    void UseUnavailableStorage() { setenv(KeyStorageEnvVar, blockerFile_.c_str(), 1); }

    bool StorageContainsKeyFile() const
    {
        for (auto const& entry : std::filesystem::recursive_directory_iterator(writableDir_)) {
            if (entry.path().extension() == ".key") {
                return true;
            }
        }
        return false;
    }

private:
    std::optional<std::string> originalStorageEnv_;
    std::filesystem::path baseDir_;
    std::filesystem::path writableDir_;
    std::filesystem::path blockerFile_;
};

TEST_F(PersistedKeyPairTests, PersistedPublicKeyIsStableAcrossCalls)
{
    UseWritableStorage();

    auto first = mls::GetPersistedPublicKey(nullptr, "stable-key-session", TestSignatureVersion);
    ASSERT_FALSE(first.key.empty());
    ASSERT_FALSE(first.signature.empty());

    // Repeat calls hit the in-process cache, so assert on-disk persistence directly.
    EXPECT_TRUE(StorageContainsKeyFile());

    auto second = mls::GetPersistedPublicKey(nullptr, "stable-key-session", TestSignatureVersion);
    EXPECT_EQ(first.key, second.key);
}

TEST_F(PersistedKeyPairTests, SessionUsesPersistedKeyWhenAvailable)
{
    UseWritableStorage();

    std::vector<std::pair<std::string, std::string>> failures;
    mls::Session session(
      nullptr, "persisted-session", [&](std::string const& source, std::string const& reason) {
          failures.emplace_back(source, reason);
      });

    std::shared_ptr<::mlspp::SignaturePrivateKey> transientKey;
    session.Init(MaxSupportedProtocolVersion(), 1234, "170920224557176832", transientKey);

    auto marshalled = session.GetMarshalledKeyPackage();
    ASSERT_FALSE(marshalled.empty());
    EXPECT_TRUE(failures.empty());
    EXPECT_EQ(transientKey, nullptr);

    auto persisted = mls::GetPersistedPublicKey(nullptr, "persisted-session", TestSignatureVersion);
    ASSERT_FALSE(persisted.key.empty());

    auto keyPackage = UnmarshalKeyPackage(marshalled);
    EXPECT_EQ(keyPackage.leaf_node.signature_key.data.as_vec(), persisted.key);
}

TEST_F(PersistedKeyPairTests, SessionFallsBackToTransientKeyWhenPersistedKeyFails)
{
    UseUnavailableStorage();

    ExpectSessionFallsBackToTransientKey();
}

TEST_F(PersistedKeyPairTests, SessionHonorsSuppliedTransientKeyOnReinit)
{
    UseWritableStorage();

    ExpectSessionHonorsSuppliedTransientKeyOnReinit();
}

#elif defined(DAVE_TEST_SESSION_FALLBACK)

class PersistedKeyPairTests : public DaveTests {};

// Runs in every configuration CI builds: the null implementation never yields a key.
TEST_F(PersistedKeyPairTests, SessionFallsBackToTransientKeyWithoutPersistentKeys)
{
    ExpectSessionFallsBackToTransientKey();
}

TEST_F(PersistedKeyPairTests, SessionHonorsSuppliedTransientKeyOnReinit)
{
    ExpectSessionHonorsSuppliedTransientKeyOnReinit();
}

#endif

} // namespace test
} // namespace dave
} // namespace discord
